package local.tender.ui;
import local.tender.tools.initialreport.InitialReportFormatting;
import javax.swing.*;import java.nio.file.*;import java.util.*;import java.io.IOException;
final class InitialReportOptions {
 final JCheckBox enabled=new JCheckBox("简化公司名称",true);
 final JTextField suffixes=new JTextField(InitialReportFormatting.DEFAULT_SUFFIXES);
 final JButton save=new JButton("保存名称配置");
 private final Path file;
 InitialReportOptions(Path file){
  this.file=file;suffixes.setToolTipText("逗号分隔；仅删除名称末尾后缀，较长后缀优先，可连续删除。留空则保留全称。");
  try{var p=load();enabled.setSelected(Boolean.parseBoolean(p.getProperty("enabled","true")));suffixes.setText(p.getProperty("suffixes",InitialReportFormatting.DEFAULT_SUFFIXES));}
  catch(IOException|IllegalArgumentException e){JOptionPane.showMessageDialog(null,"名称配置读取失败，本次使用默认配置："+e.getMessage());}
  save.addActionListener(e->{try{save();JOptionPane.showMessageDialog(save,"名称配置已保存，重启后继续使用。");}catch(IOException ex){JOptionPane.showMessageDialog(save,"保存失败："+ex.getMessage());}});
 }
 private Properties load()throws IOException{var p=new Properties();if(Files.exists(file))try(var in=Files.newInputStream(file)){p.load(in);}return p;}
 private void save()throws IOException{
  var p=load();String value=String.join(",",InitialReportFormatting.suffixes(suffixes.getText())),active=Boolean.toString(enabled.isSelected());
  if(value.equals(p.getProperty("suffixes"))&&active.equals(p.getProperty("enabled")))return;
  p.setProperty("suffixes",value);p.setProperty("enabled",active);Files.createDirectories(file.getParent());Path staged=Files.createTempFile(file.getParent(),"initial-names-",".tmp");
  try{try(var out=Files.newOutputStream(staged)){p.store(out,"Initial report company display settings");}
   if(Files.exists(file))Files.copy(file,file.resolveSibling(file.getFileName()+".bak"),StandardCopyOption.REPLACE_EXISTING);
   try{Files.move(staged,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(staged,file,StandardCopyOption.REPLACE_EXISTING);}
  }finally{Files.deleteIfExists(staged);}
 }
 void apply(ToolForm.State state){state.parameter("simplifyNames",Boolean.toString(enabled.isSelected()));state.parameter("companySuffixes",suffixes.getText());}
}
