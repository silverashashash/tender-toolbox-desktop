package local.tender.io;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.nio.file.*;import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class HeaderRecognitionTest {
 @TempDir Path root;
 @Test void realExportHeadersOnThirdRowAcrossMultipleSheets()throws Exception{
  Path file=root.resolve("export.xlsx");
  try(var book=new XSSFWorkbook()){
   for(String name:List.of("分标甲","分标乙")){
    var sheet=book.createSheet(name);sheet.createRow(0).createCell(0).setCellValue("导出说明");
    var header=sheet.createRow(2);var names=local.tender.tools.limitprice.LimitPriceTool.QUOTE_HEADERS;
    for(int i=0;i<names.size();i++)header.createCell(i).setCellValue(names.get(i).equals("增值税率（%）")?"增值税率或征收率（%）":names.get(i));
    sheet.createRow(3).createCell(0).setCellValue("第一轮");
   }
   try(var out=Files.newOutputStream(file)){book.write(out);}
  }
  var tables=WorkbookTable.readAll(file,local.tender.tools.limitprice.LimitPriceTool.quoteColumns());
  assertEquals(2,tables.size());for(var table:tables.values()){assertEquals(3,table.headerRow());assertEquals(1,table.rows().size());}
 }
 @Test void verticalHeadersSkipTheirFullHeightButDataBlanksStayBlank()throws Exception{
  Path file=root.resolve("vertical.xlsx");
  try(var book=new XSSFWorkbook()){
   var sheet=book.createSheet("数据");var first=sheet.createRow(0);first.createCell(0).setCellValue("包名");first.createCell(1).setCellValue("供应商");
   for(int c=0;c<2;c++)sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0,1,c,c));
   sheet.createRow(2).createCell(0).setCellValue("包1");sheet.getRow(2).createCell(1).setCellValue("甲");
   sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(2,3,0,0));sheet.createRow(3).createCell(1).setCellValue("乙");
   try(var out=Files.newOutputStream(file)){book.write(out);}
  }
  var table=WorkbookTable.read(file,Map.of("包名",List.of("包名"),"供应商",List.of("供应商")));
  assertEquals(2,table.rows().size());assertEquals("3",table.rows().get(0).get("__row"));assertEquals("",table.rows().get(1).get("包名"));
 }
 @Test void reportsOnlyMissingColumns()throws Exception{
  Path file=root.resolve("input.xlsx");WorkbookTable.write(file,"数据",List.of("包名","税率"),List.of());
  var e=assertThrows(java.io.IOException.class,()->WorkbookTable.read(file,Map.of("包名",List.of("包名"),"增值税率",List.of("增值税率"))));
  assertTrue(e.getMessage().contains("缺少必要表头：增值税率"));assertFalse(e.getMessage().contains("包名"));
 }
}
