package local.tender.io;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.zip.*;
import java.io.*;
import local.tender.core.*;
import static org.junit.jupiter.api.Assertions.*;
class FileBoundaryTest {
 @TempDir Path root;
 Path zip(String name, String content) throws Exception {
  Path z=root.resolve("input.zip");
  try(var out=new ZipOutputStream(Files.newOutputStream(z))){out.putNextEntry(new ZipEntry(name));out.write(content.getBytes());out.closeEntry();} return z;
 }
 @Test void traversalRejected() throws Exception {
  var r=SafeZip.extract(zip("../outside.txt","bad"),root.resolve("owned"),100,new TaskContext(root.resolve("task")));
  assertTrue(r instanceof Outcome.Failure<?>);assertFalse(Files.exists(root.resolve("outside.txt")));
 }
 @Test void windowsTraversalRejectedOnMac() throws Exception {
  var r=SafeZip.extract(zip("..\\outside.txt","bad"),root.resolve("owned"),100,new TaskContext(root.resolve("task")));
  assertTrue(r instanceof Outcome.Failure<?>);assertFalse(Files.exists(root.resolve("outside.txt")));
 }
 @Test void expandedBudgetStopsExtraction() throws Exception {
  var r=SafeZip.extract(zip("data.txt","1234567890"),root.resolve("owned"),9,new TaskContext(root.resolve("task")));
  assertTrue(r instanceof Outcome.Failure<?>);assertFalse(Files.exists(root.resolve("owned")));
 }
 @Test void existingDirectoryNotOwnedOrDeleted() throws Exception {
  Path owned=Files.createDirectory(root.resolve("owned"));Files.writeString(owned.resolve("keep"),"old");
  assertTrue(SafeZip.extract(zip("data.txt","new"),owned,100,new TaskContext(root.resolve("task"))) instanceof Outcome.Failure<?>);
  assertEquals("old",Files.readString(owned.resolve("keep")));
 }
 @Test void cancelledExtractionLeavesNoResult() throws Exception {
  var c=new TaskContext(root.resolve("task"));c.cancel();
  assertTrue(SafeZip.extract(zip("data.txt","data"),root.resolve("owned"),100,c) instanceof Outcome.Failure<?>);
  assertFalse(Files.exists(root.resolve("owned")));
 }
 @Test void validChinesePathExtracts() throws Exception {
  var r=SafeZip.extract(zip("专家 一/评分.txt","data"),root.resolve("owned"),100,new TaskContext(root.resolve("task")));
  assertTrue(r instanceof Outcome.Success<?>);assertEquals("data",Files.readString(root.resolve("owned/专家 一/评分.txt")));
 }
 @Test void existingOutputPreserved() throws Exception {
  Path a=Files.writeString(root.resolve("staged"),"new"),b=Files.writeString(root.resolve("result"),"old");
  assertTrue(AtomicOutput.publish(a,b,false) instanceof Outcome.Failure<?>);assertEquals("old",Files.readString(b));
 }
 @Test void publishesThenReplacesOnlyWithConsent() throws Exception {
  Path a=Files.writeString(root.resolve("staged"),"new"),b=root.resolve("result");
  assertTrue(AtomicOutput.publish(a,b,false) instanceof Outcome.Success<?>);assertEquals("new",Files.readString(b));
  a=Files.writeString(root.resolve("second"),"second");
  assertTrue(AtomicOutput.publish(a,b,true) instanceof Outcome.Success<?>);assertEquals("second",Files.readString(b));
 }
}
