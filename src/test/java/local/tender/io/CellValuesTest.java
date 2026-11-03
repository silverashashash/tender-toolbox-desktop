package local.tender.io;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class CellValuesTest {
 @TempDir Path root;
 @Test void rawNumericAndFormulaValuesAreKeptSeparatelyFromDisplay()throws Exception{
  Path file=root.resolve("rounding.xlsx");try(var w=new XSSFWorkbook()){
   var s=w.createSheet("数据");s.createRow(0).createCell(0).setCellValue("金额");var a=s.createRow(1).createCell(0);a.setCellValue(1.004);var format=w.createCellStyle();format.setDataFormat(w.createDataFormat().getFormat("0.00"));a.setCellStyle(format);
   var b=s.createRow(2).createCell(0);b.setCellFormula("A2*100");b.setCellStyle(format);try(var out=Files.newOutputStream(file)){w.write(out);}
  }
  var table=WorkbookTable.read(file,Map.of("金额",List.of("金额")));assertEquals("1.00",table.rows().get(0).get("金额"));assertEquals("1.004",WorkbookTable.numericText(table.rows().get(0),"金额"));assertEquals("100.4",WorkbookTable.numericText(table.rows().get(1),"金额"));
 }
}
