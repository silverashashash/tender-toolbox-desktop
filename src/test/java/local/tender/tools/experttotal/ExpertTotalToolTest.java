package local.tender.tools.experttotal;
import local.tender.core.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ExpertTotalToolTest {
 @TempDir Path root;
 Path file(String name,double[] scores)throws Exception{
  Path p=root.resolve(name+".xlsx");try(var w=new XSSFWorkbook()){
   var s=w.createSheet("包01");s.createRow(1).createCell(0).setCellValue("设备/材料名称: 分标甲 日期:2026-10-22");
   s.createRow(2).createCell(0).setCellValue("专家");s.createRow(3).createCell(1).setCellValue(name);
   for(int i=0;i<scores.length;i++){var r=s.createRow(4+i);r.createCell(0).setCellValue("202512020001_专家"+(i+1));r.createCell(1).setCellValue(scores[i]);}
   s.createRow(4+scores.length).createCell(0).setCellValue("平均分");s.createRow(6+scores.length);
   try(var out=Files.newOutputStream(p)){w.write(out);}
  }return p;
 }
 Outcome<ToolResult> run(List<Path> files,String count,String password)throws Exception{
  return new ExpertTotalTool().execute(new ToolRequest(ToolId.EXPERT_TOTAL,Map.of("scores",files),Map.of("expertNum",count,"maxScoreDiff","7","password",password),root.resolve("out"),Optional.empty()),new TaskContext(root));
 }
 List<List<String>> rows(Outcome<ToolResult> result)throws Exception{
  assertInstanceOf(Outcome.Success.class,result);var file=((Outcome.Success<ToolResult>)result).value().outputs().get(0);
  try(var in=Files.newInputStream(file);var w=new XSSFWorkbook(in)){var resultRows=new ArrayList<List<String>>();for(var r:w.getSheetAt(0)){var cells=new ArrayList<String>();for(var c:r)cells.add(c.getStringCellValue());resultRows.add(cells);}return resultRows;}
 }
 @Test void onlyDifferencesGreaterThanSevenFlagAcrossIndependentFiles()throws Exception{
  var r=rows(run(List.of(file("六分",new double[]{80,81,83,85,86}),file("七分",new double[]{80,81,83,85,87}),file("八分",new double[]{80,81,83,85,88})),"5",""));
  assertEquals(2,r.size());assertEquals("八分",r.get(1).get(2));assertTrue(r.get(1).get(4).contains("88"));
 }
 @Test void reportsExactDifferenceAndEveryExpertPairEvenWhenScoresRepeat()throws Exception{
  var r=rows(run(List.of(file("姓名",new double[]{80,80,88})),"3",""));
  String difference=r.get(1).get(4);assertTrue(difference.contains("分差8分"));assertTrue(difference.contains("；\n"));
  try(var in=Files.newInputStream(root.resolve("out/专家总分核验.xlsx"));var w=new XSSFWorkbook(in)){var row=w.getSheetAt(0).getRow(1);assertTrue(row.getCell(4).getCellStyle().getWrapText());assertTrue(row.getHeightInPoints()>w.getSheetAt(0).getDefaultRowHeightInPoints());}
  assertTrue(difference.contains("专家1"));assertTrue(difference.contains("专家2"));assertTrue(difference.contains("专家3"));
  assertFalse(difference.contains("第5行"));assertFalse(difference.contains("第6行"));assertFalse(difference.contains("202512020001"));assertFalse(r.get(1).get(3).contains("202512020001"));assertTrue(r.get(1).get(6).contains("第4行"));
  assertTrue(r.get(1).get(3).contains("专家1"));assertTrue(r.get(1).get(3).contains("专家2"));
 }
 @Test void exactHalfIsNotMajority()throws Exception{
  assertEquals(1,rows(run(List.of(file("半数",new double[]{80,80,82,83})),"4","")).size());
 }
 @Test void expertCountMismatchFailsWithoutOutput()throws Exception{
  assertInstanceOf(Outcome.Failure.class,run(List.of(file("人数",new double[]{80,81,82,83})),"5",""));
  assertFalse(Files.exists(root.resolve("out/专家总分核验.xlsx")));
 }
 @Test void majorityZeroProducesOneExplicitWarning()throws Exception{
  var r=rows(run(List.of(file("零分",new double[]{0,0,0,1,2})),"5",""));assertEquals(2,r.size());assertTrue(r.get(1).get(3).contains("3"));assertTrue(r.get(1).get(3).contains("0"));
 }
 @Test void unnamedScoredColumnFailsRatherThanBeingSkipped()throws Exception{
  Path file=file("缺名称",new double[]{80,81,83,85,88});
  try(var in=Files.newInputStream(file);var w=new XSSFWorkbook(in)){for(int r=4;r<9;r++)w.getSheetAt(0).getRow(r).createCell(2).setCellValue(90);try(var out=Files.newOutputStream(root.resolve("changed.xlsx"))){w.write(out);}}
  assertInstanceOf(Outcome.Failure.class,run(List.of(root.resolve("changed.xlsx")),"5",""));
 }
 @Test void missingSupplierRowOnSecondPageFailsRatherThanBeingSkipped()throws Exception{
  Path file=file("缺页头",new double[]{80,81,83,85,88});
  try(var in=Files.newInputStream(file);var w=new XSSFWorkbook(in)){var s=w.getSheetAt(0);for(int r=16;r<21;r++)s.createRow(r).createCell(1).setCellValue(90);s.createRow(21).createCell(0).setCellValue("平均分");try(var out=Files.newOutputStream(root.resolve("changed.xlsx"))){w.write(out);}}
  assertInstanceOf(Outcome.Failure.class,run(List.of(root.resolve("changed.xlsx")),"5",""));
 }
 @Test void displayRoundingDoesNotHideAnExcessiveDifference()throws Exception{
  Path file=file("精度",new double[]{80,81,83,85,87.04});
  try(var in=Files.newInputStream(file);var w=new XSSFWorkbook(in)){var style=w.createCellStyle();style.setDataFormat(w.createDataFormat().getFormat("0.0"));for(int r=4;r<9;r++)w.getSheetAt(0).getRow(r).getCell(1).setCellStyle(style);try(var out=Files.newOutputStream(root.resolve("changed.xlsx"))){w.write(out);}}
  assertEquals(2,rows(run(List.of(root.resolve("changed.xlsx")),"5","")).size());
 }
 @Test void encryptedWorkbookRejectsWrongPasswordAndAcceptsCorrectOne()throws Exception{
  Path plain=file("加密",new double[]{80,81,83,85,88}),encrypted=root.resolve("encrypted.xlsx");
  var info=new org.apache.poi.poifs.crypt.EncryptionInfo(org.apache.poi.poifs.crypt.EncryptionMode.agile);var encryptor=info.getEncryptor();encryptor.confirmPassword("fixture-only");
  try(var fs=new org.apache.poi.poifs.filesystem.POIFSFileSystem()){
   try(var stream=encryptor.getDataStream(fs)){Files.copy(plain,stream);}try(var out=Files.newOutputStream(encrypted)){fs.writeFilesystem(out);}
  }
  var failure=run(List.of(encrypted),"5","wrong-fixture");assertInstanceOf(Outcome.Failure.class,failure);assertFalse(failure.toString().contains("wrong-fixture"));
  assertEquals(2,rows(run(List.of(encrypted),"5","fixture-only")).size());
 }
}
