package local.tender.ui;
import javax.swing.*;
import local.tender.tools.objective.ObjectiveTool.ScoringMethod;
import local.tender.tools.objective.ObjectiveTool.Selection;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.List;
final class ObjectiveSelectionDialog {
 private ObjectiveSelectionDialog(){}
 static Map<Selection,String> choose(Component parent,List<ScoringMethod> methods,Map<Selection,String> previous){
  var tabs=new JTabbedPane();var tables=new ArrayList<JTable>();var models=new ArrayList<DefaultTableModel>();
  for(int i=0;i<methods.size();i++){
   var method=methods.get(i);
   var model=new DefaultTableModel(new String[]{"核验","项目名称","评分细则（原表完整名称）","分值步长（可空）"},0){
    public Class<?> getColumnClass(int col){return col==0?Boolean.class:String.class;}
    public boolean isCellEditable(int row,int col){return col==0||col==3;}
   };
   for(var item:method.criteria()){var key=new Selection(method.id(),item.name());model.addRow(new Object[]{previous.containsKey(key),String.join("、",item.projects()),item.name(),previous.getOrDefault(key,"")});}
   var table=new JTable(model);table.setRowHeight(30);table.getColumnModel().getColumn(0).setMaxWidth(55);table.getColumnModel().getColumn(1).setPreferredWidth(230);table.getColumnModel().getColumn(2).setPreferredWidth(440);table.getColumnModel().getColumn(3).setPreferredWidth(140);tables.add(table);models.add(model);
   var page=new JPanel(new BorderLayout(8,8));
   var packages=new JTextArea("适用范围："+String.join("、",method.packages()));packages.setEditable(false);packages.setLineWrap(true);packages.setWrapStyleWord(true);packages.setRows(3);page.add(new JScrollPane(packages),BorderLayout.NORTH);
   var scroll=new JScrollPane(table);scroll.setPreferredSize(new Dimension(900,Math.min(420,method.criteria().size()*30+30)));page.add(scroll,BorderLayout.CENTER);
   var buttons=new JPanel(new FlowLayout(FlowLayout.LEFT));var all=new JButton("本页全选");var none=new JButton("本页全不选");
   all.addActionListener(e->{for(int r=0;r<model.getRowCount();r++)model.setValueAt(true,r,0);});none.addActionListener(e->{for(int r=0;r<model.getRowCount();r++)model.setValueAt(false,r,0);});buttons.add(all);buttons.add(none);page.add(buttons,BorderLayout.SOUTH);
   tabs.addTab("评分办法"+(i+1)+"（"+method.criteria().size()+"项 / "+method.packages().size()+"包）",page);
  }
  var panel=new JPanel(new BorderLayout(8,8));panel.add(new JLabel("各评分办法独立勾选，仅核验对应的包；不需要核验的标签页可全部留空。"),BorderLayout.NORTH);panel.add(tabs,BorderLayout.CENTER);
  while(JOptionPane.showConfirmDialog(parent,panel,"选择客观评分项",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION){
   for(var table:tables)if(table.isEditing())table.getCellEditor().stopCellEditing();var result=new LinkedHashMap<Selection,String>();
   try{for(int i=0;i<models.size();i++){var model=models.get(i);for(int r=0;r<model.getRowCount();r++)if(Boolean.TRUE.equals(model.getValueAt(r,0))){String step=Objects.toString(model.getValueAt(r,3),"").trim();if(!step.isEmpty()&&new BigDecimal(step).signum()<=0)throw new IllegalArgumentException();result.put(new Selection(methods.get(i).id(),(String)model.getValueAt(r,2)),step);}}
    if(result.isEmpty()){JOptionPane.showMessageDialog(parent,"请至少勾选一项。");continue;}return result;
   }catch(IllegalArgumentException e){JOptionPane.showMessageDialog(parent,"分值步长请留空或填写大于0的数字。");}
  }
  return null;
 }
}
