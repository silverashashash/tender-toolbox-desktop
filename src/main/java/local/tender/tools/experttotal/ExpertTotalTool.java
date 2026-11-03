package local.tender.tools.experttotal;
import local.tender.core.*;
import local.tender.io.WorkbookOutput;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.EncryptedDocumentException;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

public final class ExpertTotalTool implements ToolHandler {
 public ToolId id(){return ToolId.EXPERT_TOTAL;}
 public Outcome<ToolResult> execute(ToolRequest request,TaskContext context)throws Exception{
  try {
   int experts=Integer.parseInt(request.parameters().getOrDefault("expertNum","5"));
   if(experts<1||experts>1_048_569)throw new IllegalArgumentException("专家人数超出Excel可容纳的有效范围");
   BigDecimal threshold=new BigDecimal(request.parameters().getOrDefault("maxScoreDiff","7"));if(threshold.signum()<0)throw new IllegalArgumentException("分差阈值不能为负数");
   var files=request.inputs().get("scores");if(files==null||files.isEmpty())throw new IllegalArgumentException("请选择专家总分工作簿");
   var results=new ArrayList<List<String>>();int checked=0;
   for(Path file:files){
    context.progress("读取专家总分："+file.getFileName(),0,OptionalLong.empty());context.checkCancelled();
    try(var in=Files.newInputStream(file);var workbook=WorkbookFactory.create(in,request.parameters().getOrDefault("password",""))){
     var reader=new Cells(workbook);
     String common=workbook.getNumberOfSheets()==0?"":reader.text(workbook.getSheetAt(0),1,0);
     for(Sheet sheet:workbook){
      String title=reader.text(sheet,1,0);if(title.isBlank())title=common;
      var match=Pattern.compile("材料名称[:：]\\s*(.*?)\\s*日期[:：]").matcher(title);
      if(!match.find()||match.group(1).isBlank())throw new IOException(file.getFileName()+" / "+sheet.getSheetName()+" 缺少材料名称及日期");
      String section=match.group(1);
      for(int base=0;base<=sheet.getLastRowNum();base+=experts+7){
       context.checkCancelled();Row names=sheet.getRow(base+3);
       int columns=names==null?0:names.getLastCellNum();boolean hasScores=false;
       for(int r=base+4;r<=Math.min(sheet.getLastRowNum(),base+experts+3);r++){
        Row scoreRow=sheet.getRow(r);if(scoreRow==null)continue;columns=Math.max(columns,scoreRow.getLastCellNum());
        for(int c=1;c<scoreRow.getLastCellNum();c++)if(!reader.text(sheet,r,c).isBlank())hasScores=true;
       }
       if(names==null){if(hasScores)throw new IOException(sheet.getSheetName()+" 第"+(base+4)+"行缺少供应商名称，但本页存在评分");continue;}

       if(!reader.text(sheet,base+experts+4,0).contains("平均分"))throw new IOException(sheet.getSheetName()+" 第"+(base+1)+"行起专家人数/分页格式与设置不符");

       var expertLabels=new ArrayList<String>();
       for(int j=0;j<experts;j++){
        String label=reader.text(sheet,base+4+j,0).trim();
        int separator=label.indexOf('_');if(separator>=0)label=label.substring(separator+1).trim();
        expertLabels.add(label.isEmpty()?"专家姓名未填写":label);
       }
       for(int col=1;col<columns;col++){
        String supplier=reader.text(sheet,base+3,col);if(supplier.isBlank()){
         for(int j=0;j<experts;j++)if(!reader.text(sheet,base+4+j,col).isBlank())throw new IOException(sheet.getSheetName()+" 有分数但缺少投标人名称，列"+(col+1));continue;
        }
        var scores=new ArrayList<BigDecimal>();
        for(int j=0;j<experts;j++){
         String text=reader.numeric(sheet,base+4+j,col).trim();
         try{var score=new BigDecimal(text);if(score.signum()<0)throw new NumberFormatException();scores.add(score.stripTrailingZeros());}
         catch(NumberFormatException e){throw new IOException(file.getFileName()+" / "+sheet.getSheetName()+" 第"+(base+5+j)+"行第"+(col+1)+"列分数为空或无效");}
        }
        checked++;var counts=new LinkedHashMap<BigDecimal,Integer>();for(var score:scores)counts.merge(score,1,Integer::sum);
        var majority=new ArrayList<String>();for(var e:counts.entrySet())if(e.getValue()>experts/2){
         var namesAtScore=new ArrayList<String>();for(int k=0;k<scores.size();k++)if(scores.get(k).compareTo(e.getKey())==0)namesAtScore.add(expertLabels.get(k));
         majority.add(e.getValue()+"个相同分数："+e.getKey().toPlainString()+"；专家："+String.join("、",namesAtScore));
        }
        var differences=new LinkedHashSet<String>();
        for(int i=0;i<scores.size();i++){context.checkCancelled();for(int j=i+1;j<scores.size();j++){
         var high=scores.get(i).max(scores.get(j));var low=scores.get(i).min(scores.get(j));
         var difference=high.subtract(low);
         if(difference.compareTo(threshold)>0){
          int highIndex=scores.get(i).compareTo(scores.get(j))>0?i:j;int lowIndex=highIndex==i?j:i;
          differences.add("分差"+difference.stripTrailingZeros().toPlainString()+"分：高分"+high.toPlainString()+"（"+expertLabels.get(highIndex)+"），低分"+low.toPlainString()+"（"+expertLabels.get(lowIndex)+"）");
         }
        }}
        if(!majority.isEmpty()||!differences.isEmpty())results.add(List.of(section,sheet.getSheetName(),supplier,String.join("；",majority),String.join("；\n",differences),file.getFileName().toString(),"第"+(base+4)+"行第"+(col+1)+"列"));
       }
      }
     }
    }
   }
   if(checked==0)throw new IOException("未找到可核验的专家评分");
   return WorkbookOutput.publish(request,context,"专家总分核验.xlsx","核验结果",List.of("分标名称","包名称","投标人","超过半数同分","分差超过阈值","来源文件","投标人位置"),results,List.of());
  }catch(EncryptedDocumentException e){return Outcome.fail("WORKBOOK_PASSWORD","工作簿加密：未提供正确口令或加密格式不支持");}
  catch(IOException|IllegalArgumentException e){return Outcome.fail("INPUT_INVALID",e.getMessage());}
 }
 private static final class Cells {
  private final DataFormatter formatter=new DataFormatter(Locale.ROOT);private final FormulaEvaluator evaluator;
  Cells(Workbook workbook){evaluator=workbook.getCreationHelper().createFormulaEvaluator();}
  String numeric(Sheet sheet,int r,int c)throws IOException{Row row=sheet.getRow(r);return local.tender.io.CellValues.read(row==null?null:row.getCell(c),formatter,evaluator).numericText();}
  String text(Sheet sheet,int r,int c)throws IOException{
   Row row=sheet.getRow(r);Cell cell=row==null?null:row.getCell(c);if(cell==null)return "";
   return local.tender.io.CellValues.read(cell,formatter,evaluator).display();
  }
 }
}
