package local.tender.ui;
import local.tender.core.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ToolFormTest {
 @TempDir Path root;
 @Test void formsBindInputKeysToExplicitToolIdentity(){
  assertEquals(List.of("fileA","fileB"),ToolForm.forTool(ToolId.PERFORMANCE_SCORE).inputs().stream().map(ToolForm.Input::key).toList());
  assertEquals(List.of("scores"),ToolForm.forTool(ToolId.EXPERT_TOTAL).inputs().stream().map(ToolForm.Input::key).toList());
  assertTrue(ToolForm.forTool(ToolId.EXPERT_TOTAL).inputs().get(0).multiple());
  assertEquals("否决事项公示.docx",ToolForm.forTool(ToolId.REJECTION_NOTICE).outputName());
 }
 @Test void perToolStateKeepsMultiplePathsAndDefaultsOutputBesideLatestInput(){
  var state=new ToolForm.State(ToolForm.forTool(ToolId.EXPERT_TOTAL),root);
  Path a=root.resolve("one/a.xlsx"),b=root.resolve("two/b.xlsx");state.select("scores",List.of(a,b));
  assertEquals(b.getParent(),state.output());var request=state.request(false);
  assertEquals(List.of(a,b),request.inputs().get("scores"));assertEquals("5",request.parameters().get("expertNum"));
  state.output(root.resolve("custom"));assertEquals(root.resolve("custom"),state.request(false).outputDirectory());
  var other=new ToolForm.State(ToolForm.forTool(ToolId.CONTACT_WARNING),root);assertThrows(IllegalArgumentException.class,()->other.request(false));
 }
}
