package local.tender.io;
import local.tender.core.*;import org.apache.poi.ss.usermodel.*;import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.nio.file.*;import java.util.*;import java.io.*;
public final class WorkbookTable {
 public record Table(List<Map<String,String>> rows,int headerRow){}
 private WorkbookTable(){}
 public static Table read(Path file,Map<String,List<String>> columns)throws IOException{
  try(var in=Files.newInputStream(file);var workbook=WorkbookFactory.create(in)){
   if(workbook.getNumberOfSheets()==0)throw new IOException("工作簿没有工作表："+file.getFileName());
   return readSheet(workbook,workbook.getSheetAt(0),columns);
  }catch(RuntimeException e){throw new IOException(file.getFileName()+" 无法读取："+e.getMessage(),e);}
 }
 public static Map<String,Table> readAll(Path file,Map<String,List<String>> columns)throws IOException{return readAll(file,columns,Map.of());}
 public static Map<String,Table> readAll(Path file,Map<String,List<String>> columns,Map<String,List<String>> optional)throws IOException{
  try(var in=Files.newInputStream(file);var workbook=WorkbookFactory.create(in)){
   var result=new LinkedHashMap<String,Table>();for(Sheet sheet:workbook)result.put(sheet.getSheetName(),readSheet(workbook,sheet,columns,optional));return result;
  }catch(RuntimeException e){throw new IOException(file.getFileName()+" 无法读取："+e.getMessage(),e);}
 }
 private static Table readSheet(Workbook workbook,Sheet sheet,Map<String,List<String>> columns)throws IOException{return readSheet(workbook,sheet,columns,Map.of());}
 private static Table readSheet(Workbook workbook,Sheet sheet,Map<String,List<String>> columns,Map<String,List<String>> optional)throws IOException{
  var formatter=new DataFormatter(Locale.ROOT);var evaluator=workbook.getCreationHelper().createFormulaEvaluator();
   Map<String,Integer> indexes=null;int header=-1;Set<String> best=Set.of();int bestRow=-1;
   for(Row row:sheet){
    Map<String,Integer> found=new LinkedHashMap<>();
    for(var column:columns.entrySet())for(Cell cell:headerCells(sheet,row.getRowNum()))if(column.getValue().contains(formatter.formatCellValue(cell,evaluator).trim())){
     if(found.putIfAbsent(column.getKey(),cell.getColumnIndex())!=null)throw new IOException("表头重复："+column.getKey()+"，行"+(row.getRowNum()+1));
    }
    if(found.size()>best.size()){best=Set.copyOf(found.keySet());bestRow=row.getRowNum()+1;}
    if(found.size()==columns.size()){indexes=found;header=row.getRowNum();break;}
   }
   if(indexes==null){var missing=new LinkedHashSet<>(columns.keySet());missing.removeAll(best);throw new IOException(sheet.getSheetName()+" 缺少必要表头："+String.join("、",missing)+(bestRow>0?"（最接近的表头在第"+bestRow+"行）":""));}
   var headerCells=headerCells(sheet,header);
   for(var column:optional.entrySet())for(Cell cell:headerCells)if(column.getValue().contains(formatter.formatCellValue(cell,evaluator).trim())){
    if(indexes.putIfAbsent(column.getKey(),cell.getColumnIndex())!=null)throw new IOException("表头重复："+column.getKey()+"，行"+(header+1));
   }
   // The data starts below every selected vertical header merge.
   int dataStart=header+1;
   for(var range:sheet.getMergedRegions())if(range.getFirstColumn()==range.getLastColumn()&&indexes.containsValue(range.getFirstColumn())&&range.getFirstRow()<=header&&range.getLastRow()>=header)dataStart=Math.max(dataStart,range.getLastRow()+1);
   var result=new ArrayList<Map<String,String>>();
   for(int i=dataStart;i<=sheet.getLastRowNum();i++){
    Row row=sheet.getRow(i);if(row==null)continue;var values=new LinkedHashMap<String,String>();boolean empty=true;
    for(var column:indexes.entrySet()){
     Cell cell=row.getCell(column.getValue());var parsed=CellValues.read(cell,formatter,evaluator);
     String value=parsed.display();values.put(column.getKey(),value);if(parsed.number().isPresent())values.put("__numeric:"+column.getKey(),parsed.numericText());if(!value.isBlank())empty=false;
    }
    if(!empty){values.put("__row",Integer.toString(i+1));result.add(Collections.unmodifiableMap(values));}
   }
   return new Table(List.copyOf(result),header+1);
 }

 // Only vertical header merges inherit their anchor. Horizontal group titles and data
 // cells are never forward-filled, so unrelated labels and missing values stay distinct.
 private static Collection<Cell> headerCells(Sheet sheet,int rowIndex){
  var cells=new TreeMap<Integer,Cell>();Row row=sheet.getRow(rowIndex);
  if(row!=null)for(Cell cell:row)cells.put(cell.getColumnIndex(),cell);
  for(var range:sheet.getMergedRegions())if(range.getFirstColumn()==range.getLastColumn()&&range.getFirstRow()<=rowIndex&&range.getLastRow()>=rowIndex){
   Row anchor=sheet.getRow(range.getFirstRow());if(anchor!=null){Cell cell=anchor.getCell(range.getFirstColumn());if(cell!=null)cells.put(range.getFirstColumn(),cell);}
  }
  return cells.values();
 }

 public static String percentText(Map<String,String> row,String key){
  String display=row.get(key),raw=numericText(row,key);
  if(display!=null&&(display.endsWith("%")||display.endsWith("％"))){
   if(row.containsKey("__numeric:"+key))return new java.math.BigDecimal(raw).movePointRight(2).stripTrailingZeros().toPlainString();
   return display.substring(0,display.length()-1).trim();
  }
  return raw;
 }
 public static String numericText(Map<String,String> row,String key){return row.getOrDefault("__numeric:"+key,row.get(key));}
 private static int displayWidth(String text){return text.codePoints().map(c->c>255?2:1).sum();}
 public static void write(Path file,String sheetName,List<String> headers,List<List<String>> rows)throws IOException{
  try(var workbook=new XSSFWorkbook()){
   var sheet=workbook.createSheet(sheetName);var font=workbook.createFont();font.setBold(true);var style=workbook.createCellStyle();style.setFont(font);
   var head=sheet.createRow(0);for(int i=0;i<headers.size();i++){var c=head.createCell(i);c.setCellValue(headers.get(i));c.setCellStyle(style);sheet.setColumnWidth(i,Math.min(50,Math.max(14,headers.get(i).length()*3))*256);}
   var multiline=workbook.createCellStyle();multiline.setWrapText(true);multiline.setVerticalAlignment(VerticalAlignment.TOP);
   for(var values:rows)for(int j=0;j<values.size();j++)if(values.get(j).contains("\n")){
    int longest=Arrays.stream(values.get(j).split("\n",-1)).mapToInt(WorkbookTable::displayWidth).max().orElse(0);
    sheet.setColumnWidth(j,Math.max(sheet.getColumnWidth(j),Math.min(100,longest+3)*256));
   }
   for(int i=0;i<rows.size();i++){
    var row=sheet.createRow(i+1);int heightLines=1;
    for(int j=0;j<rows.get(i).size();j++){
     String value=rows.get(i).get(j);var cell=row.createCell(j);cell.setCellValue(value);
     if(value.contains("\n")){
      cell.setCellStyle(multiline);int width=Math.max(1,sheet.getColumnWidth(j)/256-2),lines=0;
      for(String line:value.split("\n",-1))lines+=Math.max(1,(displayWidth(line)+width-1)/width);
      heightLines=Math.max(heightLines,lines);
     }
    }
    if(heightLines>1)row.setHeightInPoints(Math.min(409f,heightLines*sheet.getDefaultRowHeightInPoints()+4));
   }
   sheet.createFreezePane(0,1);if(!headers.isEmpty())sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0,rows.size(),0,headers.size()-1));
   try(var out=Files.newOutputStream(file,StandardOpenOption.CREATE_NEW)){workbook.write(out);}
  }
 }
}
