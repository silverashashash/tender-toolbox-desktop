package local.tender.ui;
import local.tender.core.*;import local.tender.tools.contactwarning.ContactWarningTool;
import org.junit.jupiter.api.Test;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
class ToolRegistryTest {
 @Test void dispatchesByIdentityAndDoesNotExposeUnimplementedTools(){
  var registry=new ToolRegistry(List.of(new ContactWarningTool()));
  assertEquals(13,registry.catalog().size());
  assertInstanceOf(ContactWarningTool.class,((Outcome.Success<ToolHandler>)registry.find(ToolId.CONTACT_WARNING)).value());
  assertInstanceOf(Outcome.Failure.class,registry.find(ToolId.PERFORMANCE_SCORE));
  assertThrows(IllegalArgumentException.class,()->new ToolRegistry(List.of(new ContactWarningTool(),new ContactWarningTool())));
 }
}
