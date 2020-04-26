class Filter{ // capsule for all the filtering behavior
  ArrayList<Lens> Lenses;
  int selected = 0, selected_time = 0;
  int mode = 0; // numeric 0-4, decides what filtering to do to saccades between potentially multiple lenses (and also the foreground fixations)
  Frame f;
  ArrayList<Integer> inst = new ArrayList<Integer>();
  Filter(Frame f){ Lenses = new ArrayList<Lens>(); this.f=f;}
  void apply_copy(Frame f){
    Filter fi = new Filter(f);
    for(Lens l: Lenses){
      Lens li = new Lens(f);
      li.mX = l.mX; li.mY=l.mY; li.LS=l.LS; li.WINDOW = l.WINDOW; li.FACTOR = l.FACTOR;
      fi.Lenses.add(li);
    }
    f.FILTER = fi; f.FILTER.update_all();
  }
  void draw(){
    for(int i=0;i<Lenses.size();i++){Lenses.get(i).draw(i==selected);}
    if(Lenses.size()>0){ // draw the line marking the selected lens
      f.base.stroke(grey(50)); f.base.strokeWeight(2);
      float h = height - 100 + (60.0*selected + 30)/Lenses.size();
      f.base.line(100, h, width-100, h);
    }
  }
  
  void update_all(){
    for(Lens l : Lenses){l.update(l.mX, l.mY);}
    for(NodeCurve n : f.node_curves){n.altt=true;}
  }
  void move(int mX, int mY){
    if(Lenses.size()>0){
      int current = 0;
      if(Lenses.get(selected).inside(mX, mY)){
        current=selected;
      }else{
        float mindist = sq(mX-Lenses.get(0).mX) + sq(mY-Lenses.get(0).mY);
        float newdist = 0;
        for(int i=1;i<Lenses.size();i++){
          newdist = sq(mX-Lenses.get(i).mX) + sq(mY-Lenses.get(i).mY);
          if(newdist < mindist){mindist=newdist;current=i;}
        }
      }
      if(current!=selected){selected=current;selected_time=millis();}
      else if(millis() - selected_time > 150){Lenses.get(selected).update(mX,mY);}
    }
  }
  void adjust(MouseEvent event){
    if(Lenses.size()>0){
      Lens LENS = Lenses.get(selected);
      LENS.adjust(event);//let the lens apply the rules for reshaping itself
      update_all();
    }
  }
  
  boolean can_draw_curve(NodeCurve G){
    if( !f.TIME.within(G.t) ){return false;} // no drawing, since out of time frame
    if( !f.TIME.within(G.t + G.dt) ){return false;}
    //if( G.dist < BRIEF_MIN && !SHOW_SHORT ){return false;} // short is hidden
    if( G.dist < BRIEF_MIN){ return SHOW_SHORT; }
    if( G.dist >= BRIEF_MIN ){if( (!SHOW_GLANCE && G.is_glance_edge()) || (!SHOW_BASIC && !G.is_glance_edge()) ){return false;}} // type is hidden
    if(Lenses.size()==0){return true;} // degenerate case
    selected = min(selected, Lenses.size()-1); // sanity check
    if(mode==0){ // made up only-use-selected-guy situation for setting up new architecture with
      Lens LENS = Lenses.get(selected);
      if(G.dist<BRIEF_MIN){
        return LENS.safe_t(G.x1t[selected],G.y1t[selected], G.xst[selected][G.xst[selected].length-1], G.yst[selected][G.yst[selected].length-1]);
      }else{ // use the LENS accept function to check if I cross the line
          return LENS.accept_t(G.x1t[selected], G.y1t[selected], G.xst[selected], G.yst[selected]);
      }
    }else if(mode==1){ // replacing effects of F
      Lens LENS = Lenses.get(selected);
      return LENS.inside(G.x1,G.y1) != LENS.inside( G.xs[G.xs.length-1],G.ys[G.xs.length-1] );
    }else if(mode==2){
      for(Lens LENS : Lenses){
        if( LENS.inside(G.x1,G.y1) != LENS.inside( G.xs[G.xs.length-1],G.ys[G.xs.length-1] ) ){return true;};
      }
      return false;
    }else if(mode==3){
      for(int i=0;i<Lenses.size();i++){
        if( !Lenses.get(i).accept_t(G.x1t[i], G.y1t[i], G.xst[i], G.yst[i]) ){return false;}
      }
      return true;
    }else if(mode==4){
      boolean has_left=false, has_right=false;
      for(Lens LENS : Lenses){
        if(LENS.inside(G.x1,G.y1) && (!LENS.inside( G.xs[G.xs.length-1],G.ys[G.xs.length-1] ))){has_left=true;}
        if((!LENS.inside(G.x1,G.y1)) && LENS.inside( G.xs[G.xs.length-1],G.ys[G.xs.length-1] )){has_right=true;}
      }
      return has_left && has_right;
    }
    return false;
  }
  
  void foreground(boolean isgen){
    if(Lenses.size()==0){return;} // degenerate case, do nothing
    inst = new ArrayList<Integer>();
    for(int i=0;i<f.nodes.size();i++){
      ArrayList<Integer> results = new ArrayList<Integer>();
      for(int k=0;k<Lenses.size();k++){
        if(isgen){results.add(f.nodes.get(i).general_value(Lenses.get(k)));}
        else{results.add(f.nodes.get(i).alternate_value(Lenses.get(k)));}
      }
      if(mode==0 || mode==1){ // basic mode, only uses the selected lens
        inst.add(results.get(selected));
      }else if(mode==2){ // treats all the lenses as one big shared lens
        int calculated = 0;
        for(int r : results){
          if(calculated==0 || r==1){calculated=r;} // white dominates, grey loses to anything
          else if(calculated==2 || r==2 || calculated+r==7){calculated=2;} // glance between lenses
        }
        inst.add(calculated);
      }else if(mode==3 || mode==4){
        boolean has_left=false; boolean has_right=false; boolean has_in=false;
        for(int r : results){
          if(r==1){has_in=true;}
          else if(r==2){has_left=true;has_right=true;}
          else if(r==3){has_left=true;}
          else if(r==4){has_right=true;}
        }
        if(!has_in){inst.add(0);}
        else if(!has_left && !has_right){inst.add(0);}
        else if(has_left && has_right){inst.add(2);}
        else if(has_left){inst.add(3);}
        else if(has_right){inst.add(4);}
        else{inst.add(1);}
      }else{
        inst.add(0);
      }
    }
    // hist stuff can go here
    float tval = 0;
    if(LHIST || THIST){ tval = min(1, (millis()-swap_time)/1000); } else { tval = 1 - min(1, (millis()-swap_time)/1000); }
    if(tval>0){
      if(THIST||PHIST){f.timeHist(inst, tval, isgen);}
      else{f.lensHist(inst, tval, isgen);}
    }else{ // draw normally
      for(int i=0;i<f.nodes.size();i++){
        if(isgen){ f.nodes.get(i).draw_self(inst.get(i), 0); }
        else{ f.nodes.get(i).draw_self(inst.get(i), 1); }
        if(TRAVEL_LINES && inst.get(i)==3 && inst.get(i+1)==1){ // the Travel lines
          int k = i+1; strokeWeight(2);
          while(k<f.nodes.size() && (inst.get(k)==1 || inst.get(k)==2) ){k++;}
          if(k<f.nodes.size() && inst.get(k)==4){
            Node ni = f.nodes.get(i); Node nk = f.nodes.get(k);
            f.base.stroke(o1(60)); f.base.line(ni.x, ni.y, (ni.x+nk.x)/2, (ni.y+nk.y)/2);
            f.base.stroke(o3(60)); f.base.line(nk.x, nk.y, (ni.x+nk.x)/2, (ni.y+nk.y)/2);
          }
        }
      }
    }
  }
  void filtered_timeline(){
    strokeWeight(1);
    if(Lenses.size()>1){ // draw the selection line
      strokeWeight(3); stroke(grey(30));
      float y = height-100 + (60.0*selected + 30)/Lenses.size();
      line( TBUFFER, y, TBUFFER+TWIDTH, y);
    }
    if(Lenses.size()==0 || (!ALTERNATE&&!GENERAL) ){ // degenerate case, just make grey marks
      for(int i=0;i<f.nodes.size();i++){
        f.nodes.get(i).time_mark(0, 0, 1);
      }
    }else if(ALTERNATE){ // use the alternate valuation
      for(int k=0;k<Lenses.size();k++){
        for(int i=0;i<f.nodes.size();i++){
          f.nodes.get(i).time_mark(f.nodes.get(i).alternate_value(Lenses.get(k)), k, Lenses.size());
        }
      }
    }else{ // use the general valuation
      for(int k=0;k<Lenses.size();k++){
        for(int i=0;i<f.nodes.size();i++){
          f.nodes.get(i).time_mark(f.nodes.get(i).general_value(Lenses.get(k)), k, Lenses.size());
        }
      }
    }
    // saccades
    for(NodeCurve n : f.node_curves){if(n.active){n.time_mark();}} // time marks hide behind the timeline
  }
  void connectors(){
    if(inst.size()<f.nodes.size()){println(inst.size(), f.nodes.size()); return;}
    strokeWeight(2);
    for(int i=0;i<f.nodes.size();i++){
      int val = inst.get(i);
      Node n = f.nodes.get(i);
      float s = SIZE;
      if(USE_SIZE){s = s*sqrt(n.dt);}
      if(val > 0 && sq(mouseX-n.x) + sq(mouseY-n.y) < sq(s/2)){
          if(val==1){fill(white(100));stroke(white(100));}//white
          else if(val==2){stroke(o2(80));}//orange
          else if(val==3){stroke(o1(80));}//red
          else if(val==4){stroke(o3(80));}//green
          line(n.x,n.y, TBUFFER+(n.t-f.TIME.Tmin)*TWIDTH/(f.TIME.Tmax - f.TIME.Tmin), height-100);
      }
    }
  }
}
