package local.tender.ui;
import local.tender.io.WorkbookTable;
import java.nio.file.*;import java.util.*;import java.io.IOException;
final class TemplateExport {
 private TemplateExport(){}
 static List<Path> save(ToolForm form,Path folder)throws IOException{
  return save(form,folder,local.tender.tools.pricescore.PriceScoreTool.METHODS.get(0));
 }
 static List<Path> save(ToolForm form,Path folder,String priceMethod)throws IOException{
  return save(form,folder,priceMethod,"无限授");
 }
 static List<Path> save(ToolForm form,Path folder,String priceMethod,String awardMode)throws IOException{
  if(form.id()==local.tender.core.ToolId.AWARD&&!local.tender.tools.award.AwardTool.MODES.contains(awardMode))throw new IOException("请选择有效授标方式");
  Files.createDirectories(folder);var saved=new ArrayList<Path>();
  for(var input:form.inputs())if(!input.templateHeaders().isEmpty()){
   if(form.id()==local.tender.core.ToolId.AWARD&&input.key().equals("groups")&&awardMode.equals("无限授"))continue;
   boolean priceRules=form.id()==local.tender.core.ToolId.PRICE_SCORE&&input.key().equals("rules");
   var headers=priceRules?local.tender.tools.pricescore.PriceScoreTool.templateHeaders(priceMethod):input.templateHeaders();
   List<List<String>> rows=priceRules?List.of(headers.stream().map(h->h.equals("价格公式")?priceMethod:"").toList()):List.of();
   String base=input.label().replaceAll("[\\\\/:*?\"<>|]","_")+"-填写模板";Path target=folder.resolve(base+".xlsx");int suffix=2;
   while(true){try{WorkbookTable.write(target,org.apache.poi.ss.util.WorkbookUtil.createSafeSheetName(input.label()),headers,rows);break;}
    catch(FileAlreadyExistsException e){target=folder.resolve(base+"-"+(suffix++)+".xlsx");}}
   saved.add(target.toAbsolutePath());
  }
  return List.copyOf(saved);
 }
}
