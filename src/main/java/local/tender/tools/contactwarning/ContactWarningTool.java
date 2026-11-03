package local.tender.tools.contactwarning;
import local.tender.core.*;import local.tender.io.*;import java.nio.file.*;import java.util.*;import java.io.*;
import local.tender.tools.contactwarning.ContactWarningRules.*;
public final class ContactWarningTool implements ToolHandler {
 public ToolId id(){return ToolId.CONTACT_WARNING;}
 public Outcome<ToolResult> execute(ToolRequest request,TaskContext context)throws Exception{
  try{
   Path bidFile=one(request,"bidList"),contactsFile=one(request,"contacts");context.progress("读取开标表",0,OptionalLong.empty());
   var bidTable=WorkbookTable.read(bidFile,Map.of("分标名称",List.of("分标名称"),"包名称",List.of("包名称","分包名称"),"投标人名称",List.of("投标人名称")));
   var contactColumns=new LinkedHashMap<String,List<String>>();for(String name:List.of("投标人名称","投标人联系人姓名","手机号码","邮政地址","固定电话","电子邮件","传真号码"))contactColumns.put(name,List.of(name));
   var contactTable=WorkbookTable.read(contactsFile,contactColumns);var bids=new ArrayList<Bid>();var contacts=new ArrayList<Contact>();
   for(var row:bidTable.rows()){
    for(String key:List.of("分标名称","包名称","投标人名称"))if(row.get(key).isBlank())throw new IOException("开标表第"+row.get("__row")+"行缺少"+key);
    bids.add(new Bid(row.get("分标名称"),row.get("包名称"),row.get("投标人名称")));
   }
   for(var row:contactTable.rows()){
    if(row.get("投标人名称").isBlank())throw new IOException("联系人表第"+row.get("__row")+"行缺少投标人名称");
    contacts.add(new Contact(row.get("投标人名称"),row.get("固定电话"),row.get("电子邮件"),row.get("传真号码"),row.get("邮政地址"),row.get("手机号码"),row.get("投标人联系人姓名")));
   }
   var known=new HashSet<String>();contacts.forEach(c->known.add(c.supplier()));var warnings=new ArrayList<Problem>();
   bids.stream().map(Bid::supplier).distinct().filter(n->!known.contains(n)).forEach(n->warnings.add(new Problem("CONTACT_MISSING","未找到供应商联系人信息："+n,new SourceLocation(contactsFile,null,null,null))));
   context.progress("核验联系人信息",0,OptionalLong.empty());var result=ContactWarningRules.check(bids,contacts);
   var rows=result.stream().map(w->List.of(w.section(),w.pack(),w.supplier1(),w.supplier2(),w.reason(),w.detail())).toList();
   return WorkbookOutput.publish(request,context,"供应商联系人预警.xlsx","联系人预警",List.of("分标名称","包名称","投标人1","投标人2","预警原因","相同内容"),rows,warnings);
  }catch(IOException|IllegalArgumentException e){return Outcome.fail("INPUT_INVALID",e.getMessage());}
 }
 private static Path one(ToolRequest request,String key){var paths=request.inputs().get(key);if(paths==null||paths.size()!=1)throw new IllegalArgumentException("请选择一个输入文件："+key);return paths.get(0);}
}
