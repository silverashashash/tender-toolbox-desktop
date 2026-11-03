package local.tender.tools.contactwarning;
import org.junit.jupiter.api.Test;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
import local.tender.tools.contactwarning.ContactWarningRules.*;
class ContactWarningRulesTest {
 Bid bid(String pack,String supplier){return new Bid("分标一",pack,supplier);}
 Contact contact(String supplier,String mobile,String address){return new Contact(supplier,"","", "",address,mobile,"");}
 @Test void comparesOnlyDistinctSuppliersWithinSameSectionAndPackage(){
  var rows=List.of(bid("包1","甲"),bid("包1","乙"),bid("包2","丙"));
  var result=ContactWarningRules.check(rows,List.of(contact("甲","123",""),contact("乙","123",""),contact("丙","123","")));
  assertEquals(1,result.size());assertEquals("甲",result.get(0).supplier1());assertEquals("乙",result.get(0).supplier2());assertEquals("手机号码相同",result.get(0).reason());
 }
 @Test void blankFieldsAndRepeatedSupplierDoNotCreateWarnings(){
  assertTrue(ContactWarningRules.check(List.of(bid("包1","甲"),bid("包1","甲"),bid("包1","乙")),List.of(contact("甲","",""),contact("乙","",""))).isEmpty());
 }
 @Test void broadAddressIgnoredButStreetNumberTriggers(){
  var bids=List.of(bid("包1","甲"),bid("包1","乙"));
  assertTrue(ContactWarningRules.check(bids,List.of(contact("甲","","合肥市"),contact("乙","","合肥市"))).isEmpty());
  assertEquals(1,ContactWarningRules.check(bids,List.of(contact("甲","","路1号"),contact("乙","","路1号"))).size());
 }
 @Test void equalNamesAreWarningsAsOriginalRulesSpecify(){
  var a=new Contact("甲","","","","","","王一");var b=new Contact("乙","","","","","","王一");
  assertEquals("投标人联系人姓名相同",ContactWarningRules.check(List.of(bid("包1","甲"),bid("包1","乙")),List.of(a,b)).get(0).reason());
 }
 @Test void faxIsSeparateFromEmail(){
  var a=new Contact("甲","","a@example.test","123","","","");var b=new Contact("乙","","b@example.test","123","","","");
  var r=ContactWarningRules.check(List.of(bid("包1","甲"),bid("包1","乙")),List.of(a,b));assertEquals(1,r.size());assertEquals("传真号码相同",r.get(0).reason());
 }
 @Test void preservesSourcePackageIdentityAndDeduplicatesRepeatedRows(){
  var r=ContactWarningRules.check(List.of(bid("包01","甲"),bid("包01","甲"),bid("包01","乙")),List.of(contact("甲","123",""),contact("乙","123","")));
  assertEquals(1,r.size());assertEquals("包01",r.get(0).pack());
 }
}
