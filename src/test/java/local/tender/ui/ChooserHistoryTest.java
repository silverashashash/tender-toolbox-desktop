package local.tender.ui;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;
class ChooserHistoryTest {
 @TempDir Path root;
 @Test void sharesDirectoryAndPersistsAcrossInstances()throws Exception {
  Path config=root.resolve("settings.properties"),folder=Files.createDirectory(root.resolve("业务文件")),file=Files.createFile(folder.resolve("输入.xlsx"));
  var history=new ChooserHistory(config,root);history.remember(file,false);
  assertEquals(folder,new ChooserHistory(config,root).directory());
  Path output=Files.createDirectory(root.resolve("结果"));history.remember(output,true);
  assertEquals(output,new ChooserHistory(config,root).directory());
 }
 @Test void missingDirectoryFallsBackToExistingParent()throws Exception {
  var history=new ChooserHistory(root.resolve("settings.properties"),root);
  Path folder=Files.createDirectories(root.resolve("项目/输入"));history.remember(folder,true);Files.delete(folder);
  assertEquals(folder.getParent(),new ChooserHistory(root.resolve("settings.properties"),root).directory());
 }
 @Test void preservesUnrelatedSettingsAndBacksUpPreviousFile()throws Exception {
  Path config=root.resolve("settings.properties");Files.writeString(config,"other=value\n");
  new ChooserHistory(config,root).remember(root,true);
  var properties=new Properties();try(var in=Files.newInputStream(config)){properties.load(in);}
  assertEquals("value",properties.getProperty("other"));assertEquals("other=value\n",Files.readString(root.resolve("settings.properties.bak")));
 }
 @Test void absentHistoryUsesFallbackWithoutCreatingSettings(){
  Path config=root.resolve("settings.properties");assertEquals(root,new ChooserHistory(config,root).directory());assertFalse(Files.exists(config));
 }
}
