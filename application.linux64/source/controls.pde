// controls for user inputs (keys or mouse pressed, mousewheel, etc.)

void keyPressed() {
  // note making controls
  if (MainFrame.notes.mode) {
    MainFrame.notes.note_key(); 
    return;
  }
  else if (key == 's') { saveStuff(); }
  // ZOOMING
  else if (key == '+') { 
    if (mouseY<height-100) {
      z*= 1.2;
    } else {
      MainFrame.TIME.zoom();
    }
  } else if (key == '-') { 
    if (mouseY<height-100) {
      z = 1;
    } else {
      MainFrame.TIME.reset();
    }
  } else if ( (key==',' || key==127) && MainFrame.FILTER.Lenses.size()>0) { // ',' or delete to remove selected lens
    MainFrame.FILTER.Lenses.remove(MainFrame.FILTER.selected);
    MainFrame.FILTER.selected = max(MainFrame.FILTER.selected-1, 0);
  } else if ( (key=='.' || key==10) && MainFrame.FILTER.Lenses.size()<MAX_LENSES) { // '.' or enter to add a new lens
    MainFrame.FILTER.Lenses.add(new Lens(MainFrame));
    MainFrame.FILTER.selected = MainFrame.FILTER.Lenses.size()-1; 
    MainFrame.FILTER.selected_time=millis();
    MainFrame.FILTER.update_all();
  }
  else if(key=='1'){ selected_frame = 0; }
  else if(key=='2'){ selected_frame = 1; }
  else if(key=='3'){ selected_frame = 2; }
  else if(key=='4'){ selected_frame = 3; }
  else if(key=='0'){ show_all^=true;}
  // special command for applying the dynamic bundling
  else if (key=='7') { MainFrame.bundle_manager(1); }
  else if (key=='8') { MainFrame.bundle_manager(2); }
  else if (key=='9') { MainFrame.bundle_manager(3); }
  Frames[selected_frame].lower.has_changed = true;

  // backspace, deletes the closest node
  if ( key==BACKSPACE && MainFrame.nodes.size()>0 && !is_bundling) { // deletes the closest node, can't be done during bundling since it would corrupt the data
    int found = 0;
    float min_dist = sq(MainFrame.nodes.get(0).x - mouseX) + sq(MainFrame.nodes.get(0).y - mouseY);
    for (int i=1; i<MainFrame.nodes.size(); i++) {
      if (!MainFrame.TIME.within(MainFrame.nodes.get(i).t)) {
        continue;
      } // node is hidden by the time filter, so skip it
      float new_dist = sq(MainFrame.nodes.get(i).x - mouseX) + sq(MainFrame.nodes.get(i).y - mouseY);
      if (new_dist<min_dist) {
        found=i;
        min_dist=new_dist;
      }
    }
    Node f = MainFrame.nodes.get(found);
    if (f.LastNode != null) {
      f.LastNode.NextNode = f.NextNode;
    }
    if (f.NextNode != null) {
      f.NextNode.LastNode = f.LastNode;
    }
    MainFrame.nodes.remove(found);
    if (MainFrame.node_curves.size()>0) {
      if (found == MainFrame.node_curves.size()) {
        MainFrame.node_curves.remove(found-1);
      } // last guy, so just remove the edge going to it
      else {
        MainFrame.node_curves.remove(found);
      }
    }
    MainFrame.lower.has_changed=true;
  }

  //// quickstart : only really intended for development use, can remove later
  if (key=='q') { // DEV feature to load some files quickly, so I don't have to do it myself
    MainFrame.bgi = loadImage("q.png");
    MainFrame.fx = float(MainFrame.lower.base.width)/(MainFrame.bgi.width); 
    MainFrame.fy = float(MainFrame.lower.base.height)/(MainFrame.bgi.height);
    MainFrame.bgi.resize(MainFrame.lower.base.width, MainFrame.lower.base.height);
    attach("q2.csv"); 
    MainFrame.process(); 
    MainFrame.TIME.reset();
  }
}

void mouseWheel(MouseEvent event) {
  if (controller.isOpen &&mouseY < height-100 && mouseX > width-150) {
    controller.mouseWheel(event);
  } // controller gets to handle this
  else if (mouseY<height-100 && !MainFrame.notes.mode) {
    MainFrame.FILTER.adjust(event);
  }
  MainFrame.lower.has_changed = true;
}
void mouseDragged() {
  mouseClicked();
}
void mouseClicked() {
  if (mouseY > height-100) { // lower control panel gets to handle this
    if (mouseX > width-60) {
      controller.isOpen = !controller.isOpen; 
      return;
    } // swap the controls window
    else if (CLIP_MODE && (mouseX - TBUFFER)/TWIDTH  > MainFrame.TIME.Ts && (mouseX - TBUFFER)/TWIDTH < MainFrame.TIME.Te ) {
      MainFrame.TIME.movie_time_clicked();
    } else {
      MainFrame.TIME.clicked();
    }
  } else if (mouseY < 900 && mouseX<1700) {
    FrameClicked();
  } // mouse is pressed on screen, so update the lenses
  MainFrame.lower.has_changed = true;
}
void mousePressed() { 
  if (mouseButton==LEFT) { lmp=true; }
  else if (mouseButton==RIGHT) { rmp=true; }
  if (controller.isOpen && mouseY<height-100 && mouseX > width-150) {
    controller.clicked();
  } // controller gets to handle this
}
void mouseReleased() { lmp = false; rmp = false; }

void FrameClicked(){
  if(!show_all){
    if(MainFrame.notes.mode){MainFrame.notes.note_click(mouseX, mouseY);}else{MainFrame.FILTER.move(mouseX, mouseY);}
  }else{
    int c = 0;
    if(mouseY > MainFrame.base.height/2){c+=2;}
    if(mouseX > MainFrame.base.width/2){c+=1;}
    if(c != selected_frame && mouseButton==RIGHT){
      Frames[selected_frame].FILTER.apply_copy(Frames[c]); return;
    }else if(c != selected_frame && mouseButton==LEFT){
      selected_frame = c; MainFrame = Frames[selected_frame];
      if(mouseButton==RIGHT){return;}
    }
    int mX = 2*(mouseX % (MainFrame.base.width/2));
    int mY = 2*(mouseY % (MainFrame.base.height/2));
    if(MainFrame.notes.mode){MainFrame.notes.note_click(mX,mY);}else{MainFrame.FILTER.move(mX, mY);}
  }
  
}
