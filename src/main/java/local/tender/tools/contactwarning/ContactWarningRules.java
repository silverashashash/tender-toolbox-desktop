package local.tender.tools.contactwarning;
import java.util.*;import java.util.function.Function;
/** Restored from WarnConfirmEcp.findCrossBidderDuplicates/checkField; see docs/rules/contactwarning.md. */
public final class ContactWarningRules {
 public record Bid(String section,String pack,String supplier){}
 public record Contact(String supplier,String phone,String email,String fax,String address,String mobile,String person){}
 public record Warning(String section,String pack,String supplier1,String supplier2,String reason,String detail){}
 private record Group(String section,String pack){}
 private record Field(String label,Function<Contact,String> value){}
 private static final List<Field> FIELDS=List.of(new Field("固定电话",Contact::phone),new Field("电子邮件",Contact::email),new Field("传真号码",Contact::fax),new Field("邮政地址",Contact::address),new Field("手机号码",Contact::mobile),new Field("投标人联系人姓名",Contact::person));
 private ContactWarningRules(){}
 public static List<Warning> check(List<Bid> bids,List<Contact> contacts){
  Map<Group,Set<String>> groups=new LinkedHashMap<>();
  for(var bid:bids)groups.computeIfAbsent(new Group(bid.section,bid.pack),k->new LinkedHashSet<>()).add(bid.supplier);
  Map<String,List<Contact>> bySupplier=new LinkedHashMap<>();
  for(var contact:contacts)bySupplier.computeIfAbsent(contact.supplier,k->new ArrayList<>()).add(contact);
  var results=new ArrayList<Warning>();
  for(var entry:groups.entrySet()){
   if(entry.getValue().size()<2)continue;
   for(var field:FIELDS){
    Map<String,Set<String>> byValue=new LinkedHashMap<>();
    for(String supplier:entry.getValue())for(var contact:bySupplier.getOrDefault(supplier,List.of())){
     String value=field.value.apply(contact);if(value==null||value.isEmpty())continue;
     if(field.label.equals("邮政地址")&&!value.matches(".*\\d.*")&&!value.contains("号"))continue;
     byValue.computeIfAbsent(value,k->new LinkedHashSet<>()).add(supplier);
    }
    for(var value:byValue.entrySet()){
     var suppliers=new ArrayList<>(value.getValue());
     for(int i=0;i<suppliers.size();i++)for(int j=i+1;j<suppliers.size();j++)results.add(new Warning(entry.getKey().section,entry.getKey().pack,suppliers.get(i),suppliers.get(j),field.label+"相同",value.getKey()));
    }
   }
  }
  return List.copyOf(results);
 }
}
