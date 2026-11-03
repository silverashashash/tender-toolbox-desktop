package local.tender.ui;
import local.tender.core.*;
import local.tender.io.WorkbookTable;
import local.tender.tools.contactwarning.ContactWarningTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class DemoFilesTest {
 @TempDir Path root;
 @Test void bundledDemoRunsWithOneFaxWarningAndDoesNotReplaceExistingFiles() throws Exception {
  var demo=DemoFiles.create(root);
  var second=DemoFiles.create(root);
  assertNotEquals(demo.bids(),second.bids());
  var request=new ToolRequest(ToolId.CONTACT_WARNING,Map.of("bidList",List.of(demo.bids()),"contacts",List.of(demo.contacts())),Map.of(),demo.output(),Optional.empty());
  var result=new ContactWarningTool().execute(request,new TaskContext(root));
  assertInstanceOf(Outcome.Success.class,result);
  var table=WorkbookTable.read(((Outcome.Success<ToolResult>)result).value().outputs().get(0),Map.of("reason",List.of("预警原因")));
  assertEquals(1,table.rows().size());
  assertEquals("传真号码相同",table.rows().get(0).get("reason"));
 }
}
