// functionality for making and manipulating notes
class Notes{
  int note_selected = 0;
  boolean mode = false;
  ArrayList<String> notes = new ArrayList<String>();
  ArrayList<Integer> x = new ArrayList<Integer>(), y = new ArrayList<Integer>();
  Frame f;
  Notes(Frame f){this.f=f;}
  void draw(){
    f.base.fill(white(100)); f.base.stroke(white(100)); f.base.strokeWeight(2);
    for(int i=0;i<notes.size();i++){f.base.text(notes.get(i).replace("\\n","\n"), x.get(i), y.get(i));}
  }
  void note_click(int mX, int mY){
    for(int i=0; i<notes.size();i++){ if(notes.get(i).length()==0){notes.remove(i);x.remove(i);y.remove(i);i-=1;}} // clean up empty notes
    if(mouseButton == LEFT){ // add a new note
      mode=true;notes.add("");x.add(mX); y.add(mY); note_selected = notes.size()-1;
    }else if(mouseButton == RIGHT && notes.size()>0){
      // select closest note, and move to mouse pointer
      int closest = 0; float dist = sq(mX - x.get(0)) + sq(mY - y.get(0));
      for(int i=1;i<notes.size();i++){
        float d = sq(mX - x.get(i)) + sq(mY - y.get(i));
        if(d<dist){closest=i; dist=d;}
      }
      note_selected = closest;
      x.set(note_selected, mX); y.set(note_selected, mY);
    }
  }
  void note_key(){
    if(key==127 && notes.size()>0){notes.remove(notes.size()-1); x.remove(x.size()-1); y.remove(y.size()-1);} // deletes last
      if(notes.size()==0){return;}
      note_selected = min(note_selected, notes.size()-1); // sanity checks
      if(key<65535 && key>31){notes.set(note_selected, notes.get(note_selected) + key);} // implements typing
      if(key==10){notes.set(note_selected, notes.get(note_selected) + "\\n");} // implements ENTER
      if(key==8 && notes.size()> 0 && notes.get(note_selected).length()>0){ // implements backspace
        notes.set(note_selected, notes.get(note_selected).substring(0, notes.get(note_selected).length()-1));
      }
  }
  
  // write and read, used for fileAccess stuff
  void write(PrintWriter writer){
    writer.print(notes.size());writer.print(','); // first number is the length, then x,y,string ordering
    for(int i=0;i<notes.size();i++){
      writer.print(x.get(i));writer.print(',');writer.print(y.get(i));writer.print(',');writer.print(notes.get(i));writer.print(',');
    }
  }
  void read(TableRow r1){
    int l = r1.getInt(0);
    notes = new ArrayList<String>(); x = new ArrayList<Integer>(); y = new ArrayList<Integer>();
    for(int i=0;i<l;i++){ x.add(r1.getInt(3*i+1)); y.add(r1.getInt(3*i+2)); notes.add(r1.getString(3*i+3)); }
  }
}
