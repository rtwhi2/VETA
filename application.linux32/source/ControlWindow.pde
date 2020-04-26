class ControlWindow{
  boolean isOpen = true;
  color k1 = color(230,230,230,200), k2 = color(20,20,20,200); // done in RGB 256 since is defined before colorMode is
  PFont f1 = createFont("arial", 10, true);
  
  // reloading recently saved settings control functionality
  ArrayList<String> screen_sources = new ArrayList<String>();
  ArrayList<PImage> screens = new ArrayList<PImage>();
  int view_place = 0;
  float wx, wy;
  
  ControlWindow(){}
  void draw(){
    noFill();stroke(white(100));
    if(!isOpen){ // just draw the tab and do nothing else
      rect(width-60, height-80, 40, 40);
      line(width-50, height-70, width-30, height-60);
      line(width-50, height-50, width-30, height-60);
      return;
    }
    //draw the tab in the open position
    rect(width-60, height-80, 40, 40);
    line(width-30, height-70, width-50, height-60);
    line(width-30, height-50, width-50, height-60);
    //draw the backing rectangle
    fill(black(30));noStroke();
    rect(width-150, 0, 150, height-100);
    wx = width - 150; wy = 0; textFont(f1);
    // draw the 3 type inclusion options
    if(SHOW_SHORT)  {fill(k1);rect(wx    , wy, 50, 50); fill(k2);text("short",  wx+  2,wy+25);}
    else{            fill(k2);rect(wx    , wy, 50, 50); fill(k1);text("short",  wx+  2,wy+25);}
    if(SHOW_GLANCE) {fill(k1);rect(wx+ 50, wy, 50, 50); fill(k2);text("glance", wx+ 52,wy+25);}
    else{            fill(k2);rect(wx+ 50, wy, 50, 50); fill(k1);text("glance", wx+ 52,wy+25);}
    if(SHOW_BASIC)  {fill(k1);rect(wx+100, wy, 50, 50); fill(k2);text("basic",  wx+102,wy+25);}
    else{            fill(k2);rect(wx+100, wy, 50, 50); fill(k1);text("basic",  wx+102,wy+25);}
    // draw General (G), Alternative (A), and Sizing (S)
    if(GENERAL)     {fill(k1);rect(wx    , wy+50, 50, 50); fill(k2);text("Generic",             wx+  2,wy+75);}
    else{            fill(k2);rect(wx    , wy+50, 50, 50); fill(k1);text("Generic",             wx+  2,wy+75);}
    if(ALTERNATE)   {fill(k1);rect(wx+ 50, wy+50, 50, 50); fill(k2);text("Alternate",           wx+ 52,wy+75);}
    else{            fill(k2);rect(wx+ 50, wy+50, 50, 50); fill(k1);text("Alternate",           wx+ 52,wy+75);}
    if(USE_SIZE)    {fill(k1);rect(wx+100, wy+50, 50, 50); fill(k2);text("Use Sizes",           wx+102,wy+75);}
    else{            fill(k2);rect(wx+100, wy+50, 50, 50); fill(k1);text("Use Sizes",           wx+102,wy+75);}
    // draw Background (D), Interlace (I), and Colour Purpose (X)
    if(SHOW_DOTS)   {fill(k1);rect(wx    ,wy+100, 50, 50); fill(k2);text("Show\nBack",          wx+  2,wy+115);}
    else{            fill(k2);rect(wx    ,wy+100, 50, 50); fill(k1);text("Hide\nBack",          wx+  2,wy+115);}
    if(INTERLACE)   {fill(k1);rect(wx+ 50,wy+100, 50, 50); fill(k2);text("Alternate\nPathlines",wx+ 52,wy+115);}
    else{            fill(k2);rect(wx+ 50,wy+100, 50, 50); fill(k1);text("Alternate\nPathlines",wx+ 52,wy+115);}
    if(CLIP_MODE)   {fill(k1);rect(wx+100,wy+100, 50, 50); fill(k2);text("Clip\nMode",          wx+102,wy+115);}
    else{            fill(k2);rect(wx+100,wy+100, 50, 50); fill(k1);text("Clip\nMode",          wx+102,wy+115);}
    // draw time pairing (B), clustering (V), and animation (O)
    if(THIST)          {fill(k1);rect(wx    ,wy+150, 25, 50); fill(k2);text("T\nHist",          wx+  2,wy+165);}
    else{               fill(k2);rect(wx    ,wy+150, 25, 50); fill(k1);text("T\nHist",          wx+  2,wy+165);}
    if(LHIST)          {fill(k1);rect(wx+ 25,wy+150, 25, 50); fill(k2);text("L\nHist",          wx+  27,wy+165);}
    else{               fill(k2);rect(wx+ 25,wy+150, 25, 50); fill(k1);text("L\nHist",          wx+  27,wy+165);}
    if(BUNDLE)         {fill(k1);rect(wx+ 50,wy+150, 50, 50); fill(k2);text("Cont.\nBundle",       wx+ 52,wy+165);}
    else if(!MainFrame.GPU_setup){fill(0 );rect(wx+ 50,wy+150, 50, 50); fill(0 );text("Cont.\nBundle",       wx+ 52,wy+165);} // grey out since it is disabled
    else{               fill(k2);rect(wx+ 50,wy+150, 50, 50); fill(k1);text("Cont.\nBundle",       wx+ 52,wy+165);}
                        fill(k2);rect(wx+100,wy+150, 50, 50); fill(k1);text("Clear\nBundle",       wx+102,wy+165); // no conditional, just have as black
    // last row, might not need it all
    if(MainFrame.notes.mode){fill(k1);rect(wx    ,wy+200, 50, 50); fill(k2);text("add notes",           wx+  2,wy+215);}
    else{                    fill(k2);rect(wx    ,wy+200, 50, 50); fill(k1);text("add notes",           wx+  2,wy+215);}
    if(TRAVEL_LINES)        {fill(k1);rect(wx+ 50,wy+200, 50, 50); fill(k2);text("Travel\nLines",       wx+ 52,wy+215);}
    else{                    fill(k2);rect(wx+ 50,wy+200, 50, 50); fill(k1);text("Travel\nLines",       wx+ 52,wy+215);}
    if(ANIMATE)             {fill(k1);rect(wx+100,wy+200, 50, 50); fill(k2);text("Animate",             wx+102,wy+215);}
    else{                    fill(k2);rect(wx+100,wy+200, 50, 50); fill(k1);text("Animate",             wx+102,wy+215);}
    
    // 1: COL_MODE, FILTER_MODE, ALT_TIME_WINDOW
    fill(k2);rect(wx    , wy+250, 50, 50); fill(k1);text("ColMode\n " + String.format("%d", COL_MODE),              wx+  2,wy+275);
    fill(k2);rect(wx+ 50, wy+250, 50, 50); fill(k1);text("FltrMode\n "+ String.format("%d", MainFrame.FILTER.mode), wx+ 52,wy+275);
    fill(k2);rect(wx+100, wy+250, 50, 50); fill(k1);text("Alt Time\n "+ String.format("%.0f", ALT_TIME_WINDOW),     wx+102,wy+275);
    // 2: Big_K, Brief_min
    fill(k2);rect(wx    , wy+300, 50, 50); fill(k1);text("K:\n "      + String.format("%.3f", BIG_K),          wx+  2,wy+325);
    fill(k2);rect(wx+ 50, wy+300, 50, 50); fill(k1);text("MinDist\n " + String.format("%.0f", BRIEF_MIN),      wx+ 52,wy+325);
    fill(k2);rect(wx+100, wy+300, 50, 50); fill(k1);text("Size\n " + String.format("%.0f", SIZE),              wx+102,wy+325);
    fill(k2);rect(wx    , wy+350, 50, 50); fill(k1);text("Reset\nRemoved",                                     wx+  2,wy+375); // no conditional, just have as black
    fill(k2);rect(wx+ 50, wy+350, 50, 50); fill(k1);text("Delete\nInside",                                     wx+ 52,wy+375); // no conditional, just have as black
    fill(k2);rect(wx+100, wy+350, 50, 50); fill(k1);text("Delete\nOutside",                                    wx+102,wy+375); // no conditional, just have as black
    
    if(COL_MODE==0){
      //text("No\nColouring", wx+ 2, wy+420);
      strokeWeight(5); strokeCap(SQUARE);
      stroke(c3(100));line(wx+2, wy+405, wx+10, wy+405); text("Short",  wx+11, wy+408);
      stroke(c2(100));line(wx+2, wy+420, wx+10, wy+420); text("Long", wx+11, wy+423);
    }else if(COL_MODE==1){
      strokeWeight(5); strokeCap(SQUARE);
      stroke(c3(100));line(wx+2, wy+405, wx+10, wy+405); text("Short",  wx+11, wy+408);
      stroke(c1(100));line(wx+2, wy+420, wx+10, wy+420); text("Glance", wx+11, wy+423);
      stroke(c2(100));line(wx+2, wy+435, wx+10, wy+435); text("Normal", wx+11, wy+438);
    }else if(COL_MODE==2){
      strokeWeight(1);
      for(float i=0;i<1;i+=0.005){stroke(color_wheel(100, PI + i*TWO_PI));
                             line(wx+25+10*cos(i*TWO_PI), wy+425+10*sin(i*TWO_PI), wx+25+20*cos(i*TWO_PI), wy+425+20*sin(i*TWO_PI));
      }
    }else if(COL_MODE==3){
      text("Custom\nColouring", wx+ 2, wy+420);
    }
    strokeWeight(2); noStroke();
    if(show_all)            {fill(k1);rect(wx+ 50,wy+400, 50, 50); fill(k2);text("Show\nMultiple",    wx+ 52,wy+425);}
    else{                    fill(k2);rect(wx+ 50,wy+400, 50, 50); fill(k1);text("Show\nMultiple",    wx+ 52,wy+425);}
    //fill(k2);rect(wx+100, wy+50, 50, 50); fill(k1);text("Alt Time\n "+ String.format("%.0f", ALT_TIME_WINDOW),wx+102,wy+75);
    
    // settings menu, 3 guys tall
    wy = height - 150 - 300; // miniscreens will be 150px wide, 100px tall
    if(screens.size()>3){text(String.format("%d / %d", view_place, screens.size()), wx+10, wy-20);}
    for(int i=view_place;i < min(view_place+3, screens.size());i++){
      image(screens.get(i), wx, wy + 100*(i-view_place));
    }
    stroke(black(100));
    // Load and Save Buttons (at the very bottom)
    fill(k1);rect(width-150,height-150, 73, 50); fill(k2);text("Load", width-130,height-130);
    fill(k1);rect(width- 75,height-150, 73, 50); fill(k2);text("Save", width- 55,height-130);
  }
  
  void clicked(){
    println("controls clicked", mouseX, mouseY);
    if(!isOpen || mouseY>height-100 || mouseX < width-150){return;} // shouldn't be on the ControlWindow 
    if(mouseY < 50){ // First Row
      if     (width-mouseX > 100){SHOW_SHORT ^=true;}
      else if(width-mouseX >  50){SHOW_GLANCE^=true;}
      else if(width-mouseX >   0){SHOW_BASIC ^=true;}
    }else if(mouseY < 100){ // Second Row
      if     (width-mouseX > 100){GENERAL^=true;}
      else if(width-mouseX >  50){ALTERNATE^=true;}
      else if(width-mouseX >   0){USE_SIZE^=true;}
    }else if(mouseY < 150){ // Third Row
      if     (width-mouseX > 100){SHOW_DOTS^=true;}
      else if(width-mouseX >  50){INTERLACE^=true;}
      else if(width-mouseX >   0){
        CLIP_MODE^=true;
        if(MainFrame.clip!=null){if(CLIP_MODE){MainFrame.clip.loop();}else{MainFrame.clip.pause();}}
        MainFrame.movie_start=millis();
      }
    }else if(mouseY < 200){ // Fourth Row
      if     (width-mouseX > 125){THIST^=true; LHIST=false; swap_time=millis(); PHIST=true;}
      else if(width-mouseX > 100){LHIST^=true; THIST=false; swap_time=millis(); PHIST=false;}
      else if(width-mouseX >  50){BUNDLE^=MainFrame.GPU_setup;}
      else if(width-mouseX >   0){MainFrame.process();}
    }else if(mouseY < 250){ // Fifth Row, probably last
      if     (width-mouseX > 100){MainFrame.notes.mode^=true;}
      else if(width-mouseX >  50){TRAVEL_LINES^=true;}
      else if(width-mouseX >   0){ANIMATE^=true;}
    }else if(mouseY < 300){
      if     (width-mouseX > 100){COL_MODE = (COL_MODE+1)%4; for(NodeCurve n : MainFrame.node_curves){n.apply_color();} }
      else if(width-mouseX >  50){MainFrame.FILTER.mode = (MainFrame.FILTER.mode+1)%5;}
      else if(width-mouseX >   0){ALT_TIME_WINDOW = (ALT_TIME_WINDOW+2)%60;}
    }else if(mouseY < 350){ 
      if     (width-mouseX > 100){}
      else if(width-mouseX >  50){}
      else if(width-mouseX >   0){SIZE = (SIZE+10)%100;}
    }else if(mouseY < 400){ 
      if     (width-mouseX > 100){MainFrame.nodes = (ArrayList<Node>) MainFrame.nodes_backup.clone(); MainFrame.process();}
      else if(width-mouseX >  50){MainFrame.delete(true); MainFrame.process();}
      else if(width-mouseX >   0){MainFrame.delete(false); MainFrame.process();}
    }else if(mouseY < 450){ 
      if     (width-mouseX > 100){ COL_MODE = (COL_MODE+1)%4; for(NodeCurve n : MainFrame.node_curves){n.apply_color();} }
      if     (width-mouseX >  50){ show_all^=true; }
      else if(width-mouseX >   0){}
    }
    else if (mouseY > height-450 && mouseY < height-150){ // miniscreen area
      int x = (mouseY - height + 450)/100 + view_place; // id of the miniscreen clicked on
      if(x >= screen_sources.size()){return;} // sanity check, avoid loading below the last
      loadSetting(screen_sources.get(x) + ".setting");
    }
    else if (mouseY > height-150 && mouseY < height-100){ // bottom, Load/Save row
      if(mouseX < width-75){openStuff();} // Load
      else if(mouseX > width-75){saveStuff();} // Save
    }
    println("controls clicked completed");
  }
  void mouseWheel(MouseEvent event){
    if(!isOpen || mouseY>height-100 || mouseX < width-150){return;} // shouldn't be on the ControlWindow 
    if(mouseY > 250 && mouseY < 300){ // first scroll row
        if     (width-mouseX > 100){COL_MODE = (4+COL_MODE + event.getCount())%4; for(NodeCurve n : MainFrame.node_curves){n.apply_color();} }
        else if(width-mouseX >  50){MainFrame.FILTER.mode = (5+MainFrame.FILTER.mode + event.getCount())%5;}
        else if(width-mouseX >   0){ALT_TIME_WINDOW = max(0, min(60, ALT_TIME_WINDOW + 1*event.getCount()));}
    }else if(mouseY > 300 && mouseY < 350){ // second scroll row
        if     (width-mouseX > 100){ BIG_K = min(100.0, max(0.001, BIG_K * exp(0.2*event.getCount())));}
        else if(width-mouseX >  50){ // BRIEF MIN
          float NEW_BRIEF_MIN = max(0.4, min(2000, BRIEF_MIN * pow(1.1,event.getCount())));
          for(NodeCurve n : MainFrame.node_curves){ if(n.dist<BRIEF_MIN != n.dist<NEW_BRIEF_MIN){n.apply_color();} }
          BRIEF_MIN = NEW_BRIEF_MIN;
        }
        else if(width-mouseX >   0){ SIZE = min(100.0, max(1.0, SIZE * exp(0.2*event.getCount())));}
    }else if(mouseY > height-450 && mouseY < height-150){ // we are in the miniscreens bar
      view_place = max(min(view_place + event.getCount(), screens.size()-3), 0); // scroll through, limited to a range
    }
  }
}
