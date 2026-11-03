package local.tender.io;
import org.apache.poi.ss.usermodel.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Optional;
/** Owns the distinction between workbook values and display formatting. */
public final class CellValues {
 private CellValues(){}
 public record Value(String display,Optional<BigDecimal> number){public String numericText(){return number.map(n->n.stripTrailingZeros().toPlainString()).orElse(display);}}
 public static Value read(Cell cell,DataFormatter formatter,FormulaEvaluator evaluator)throws IOException{
  if(cell==null)return new Value("",Optional.empty());
  CellType type=cell.getCellType();org.apache.poi.ss.usermodel.CellValue evaluated=type==CellType.FORMULA?evaluator.evaluate(cell):null;
  if(type==CellType.ERROR||(evaluated!=null&&evaluated.getCellType()==CellType.ERROR))throw new IOException(cell.getSheet().getSheetName()+"!"+cell.getAddress()+" 为错误单元格");
  Optional<BigDecimal> number=Optional.empty();
  if(type==CellType.NUMERIC)number=Optional.of(BigDecimal.valueOf(cell.getNumericCellValue()));
  else if(evaluated!=null&&evaluated.getCellType()==CellType.NUMERIC)number=Optional.of(BigDecimal.valueOf(evaluated.getNumberValue()));
  return new Value(formatter.formatCellValue(cell,evaluator),number);
 }
}
