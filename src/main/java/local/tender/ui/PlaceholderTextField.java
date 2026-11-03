package local.tender.ui;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
/** Placeholder is painted only; it never becomes submitted field content. */
final class PlaceholderTextField extends JTextField {
 private final String placeholder;
 PlaceholderTextField(String value,String placeholder){
  super(value);this.placeholder=placeholder;setToolTipText(placeholder);
  getAccessibleContext().setAccessibleDescription(placeholder);
  getDocument().addDocumentListener(new DocumentListener(){
   public void insertUpdate(DocumentEvent e){repaint();}
   public void removeUpdate(DocumentEvent e){repaint();}
   public void changedUpdate(DocumentEvent e){repaint();}
  });
 }
 @Override protected void paintComponent(Graphics graphics){
  super.paintComponent(graphics);if(!getText().isEmpty())return;
  Graphics2D g=(Graphics2D)graphics.create();try{
   var insets=getInsets();g.clipRect(insets.left,insets.top,getWidth()-insets.left-insets.right,getHeight()-insets.top-insets.bottom);
   g.setColor(new Color(125,125,125));g.setFont(getFont().deriveFont(11f));
   var metrics=g.getFontMetrics();g.drawString(placeholder,insets.left+2,(getHeight()-metrics.getHeight())/2+metrics.getAscent());
  }finally{g.dispose();}
 }
}
