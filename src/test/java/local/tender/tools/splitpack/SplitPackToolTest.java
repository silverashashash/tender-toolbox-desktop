package local.tender.tools.splitpack;
import local.tender.core.*;import local.tender.io.*;import org.apache.poi.ss.usermodel.*;import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;import java.nio.file.*;import java.util.*;import java.math.BigDecimal;import static org.junit.jupiter.api.Assertions.*;
class SplitPackToolTest {
 @TempDir Path root;
 Outcome<ToolResult> run(List<List<String>> materials,List<List<String>> rules,Map<String,String> parameters)throws Exception{
  Path dir=Files.createTempDirectory(root,"case");Path m=dir.resolve("m.xlsx"),r=dir.resolve("r.xlsx");WorkbookTable.write(m,"物料",SplitPackTool.MATERIAL_HEADERS,materials);WorkbookTable.write(r,"分包",SplitPackTool.RULE_HEADERS,rules);
  return new SplitPackTool().execute(new ToolRequest(ToolId.SPLIT_PACK,Map.of("materials",List.of(m),"rules",List.of(r)),parameters,dir.resolve("out"),Optional.empty()),new TaskContext(root));
 }
 List<String> material(String code,String extension,String unit,String quantity,String price,String step){return List.of("1",code,"物料","长描述",unit,"规范",extension,quantity,price,step);}
 List<List<String>> rules=List.of(List.of("1","1"),List.of("2","1"),List.of("3","1"));
 @Test void conservesCompositeIdentityQuantitiesAndExactOriginalMoney()throws Exception{
  var result=run(List.of(material("A-B","C","吨","0.0105","1.123456789","0.001"),material("A","B-C","个","9","2.5","")),rules,Map.of());
  var success=assertInstanceOf(Outcome.Success.class,result);Path path=((ToolResult)success.value()).outputs().get(0);
  try(var wb=WorkbookFactory.create(path.toFile())){assertEquals(2,wb.getNumberOfSheets());var s=wb.getSheet("分包结果数据");var sums=new HashMap<String,BigDecimal>();BigDecimal money=BigDecimal.ZERO;for(int i=1;i<=s.getLastRowNum();i++){var r=s.getRow(i);String key=r.getCell(2).getStringCellValue()+"|"+r.getCell(6).getStringCellValue();sums.merge(key,new BigDecimal(r.getCell(7).getStringCellValue()),BigDecimal::add);money=money.add(new BigDecimal(r.getCell(10).getStringCellValue()));}assertEquals(0,new BigDecimal("0.0105").compareTo(sums.get("A-B|C")));assertEquals(0,new BigDecimal("9").compareTo(sums.get("A|B-C")));assertEquals(0,new BigDecimal("22.5117962962845").compareTo(money));}
 }
 @Test void tailRotationVisitsEveryPackageAndIsDeterministic()throws Exception{
  var rows=List.of(material("A","","个","6","10","1"));var target=List.of(List.of("1","0.1"),List.of("2","0.1"),List.of("3","0.1"));
  var result=run(rows,target,Map.of("mode","金额"));var success=assertInstanceOf(Outcome.Success.class,result);
  try(var wb=WorkbookFactory.create(((ToolResult)success.value()).outputs().get(0).toFile())){var s=wb.getSheet("分包结果数据");assertEquals(3,s.getLastRowNum());for(int i=1;i<=3;i++)assertEquals("2",s.getRow(i).getCell(7).getStringCellValue());}
 }
 @Test void proportionalPassMatchesTargetsAndRepeatedOutputIsStable()throws Exception{
  var targets=List.of(List.of("1","3"),List.of("2","2"),List.of("3","1"));var material=List.of(material("A","","个","60","1","1"));
  List<String> previous=null;
  for(int attempt=0;attempt<2;attempt++){var result=assertInstanceOf(Outcome.Success.class,run(material,targets,Map.of()));try(var wb=WorkbookFactory.create(((ToolResult)result.value()).outputs().get(0).toFile())){var s=wb.getSheet("分包结果数据");var actual=new ArrayList<String>();for(int i=1;i<=s.getLastRowNum();i++)actual.add(s.getRow(i).getCell(7).getStringCellValue());assertEquals(List.of("30","20","10"),actual);if(previous!=null)assertEquals(previous,actual);previous=actual;}}
 }
 @Test void enormousTailIsDistributedWithoutPerUnitIteration()throws Exception{
  var result=assertInstanceOf(Outcome.Success.class,run(List.of(material("A","","个","3000000000000","1","1")),rules,Map.of("mode","金额")));
  try(var wb=WorkbookFactory.create(((ToolResult)result.value()).outputs().get(0).toFile())){var s=wb.getSheet("分包结果数据");for(int i=1;i<=3;i++)assertEquals("1000000000000",s.getRow(i).getCell(7).getStringCellValue());}
 }
 @Test void readsUnderlyingNumericAndFormulaValuesRegardlessOfDisplayFormat()throws Exception{
  Path m=root.resolve("numeric.xlsx"),r=root.resolve("rules.xlsx");
  WorkbookTable.write(m,"物料",SplitPackTool.MATERIAL_HEADERS,List.of(material("M","","个","1","1","1")));
  WorkbookTable.write(r,"规则",SplitPackTool.RULE_HEADERS,List.of(List.of("1","1")));
  try(var wb=WorkbookFactory.create(new java.io.ByteArrayInputStream(Files.readAllBytes(m)))){var row=wb.getSheetAt(0).getRow(1);var style=wb.createCellStyle();style.setDataFormat(wb.createDataFormat().getFormat("0.00"));row.getCell(7).setCellFormula("10+0.125");row.getCell(8).setCellValue(1.123456789);row.getCell(9).setCellValue(0.001);for(int c=7;c<=9;c++)row.getCell(c).setCellStyle(style);wb.getCreationHelper().createFormulaEvaluator().evaluateAll();try(var out=Files.newOutputStream(m)){wb.write(out);}}
  try(var wb=WorkbookFactory.create(new java.io.ByteArrayInputStream(Files.readAllBytes(r)))){var cell=wb.getSheetAt(0).getRow(1).getCell(1);cell.setCellFormula("1/4");var style=wb.createCellStyle();style.setDataFormat(wb.createDataFormat().getFormat("0.0"));cell.setCellStyle(style);wb.getCreationHelper().createFormulaEvaluator().evaluateAll();try(var out=Files.newOutputStream(r)){wb.write(out);}}
  var result=assertInstanceOf(Outcome.Success.class,new SplitPackTool().execute(new ToolRequest(ToolId.SPLIT_PACK,Map.of("materials",List.of(m),"rules",List.of(r)),Map.of(),root.resolve("numeric-result"),Optional.empty()),new TaskContext(root)));
  try(var wb=WorkbookFactory.create(((ToolResult)result.value()).outputs().get(0).toFile())){var row=wb.getSheet("分包结果数据").getRow(1);assertEquals("10.125",row.getCell(7).getStringCellValue());assertEquals("1.123456789",row.getCell(9).getStringCellValue());BigDecimal total=new BigDecimal("10.125").multiply(new BigDecimal("1.123456789"));assertEquals(0,total.compareTo(new BigDecimal(row.getCell(10).getStringCellValue())));BigDecimal expected=total.divide(new BigDecimal("0.25"),4,java.math.RoundingMode.DOWN).multiply(new BigDecimal("0.25"));assertEquals(0,expected.compareTo(new BigDecimal(wb.getSheet("汇总").getRow(1).getCell(4).getStringCellValue())));}
 }
 @Test void rejectsInvalidValuesAndDuplicateIdentity()throws Exception{
  for(String value:List.of("0","-1","/"))assertInstanceOf(Outcome.Failure.class,run(List.of(material("A","","个",value,"2","")),rules,Map.of()));
  assertInstanceOf(Outcome.Failure.class,run(List.of(material("A","","个","2","0","")),rules,Map.of()));
  assertInstanceOf(Outcome.Failure.class,run(List.of(material("A","","个","2","2","0")),rules,Map.of()));
  var row=material("A","","个","2","2","");assertInstanceOf(Outcome.Failure.class,run(List.of(row,row),rules,Map.of()));
  assertInstanceOf(Outcome.Failure.class,run(List.of(row),List.of(List.of("1","0")),Map.of()));
 }
}
