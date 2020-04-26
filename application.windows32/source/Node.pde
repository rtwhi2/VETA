class Node{
  float t,x,y,dt;
  Node NextNode = null;
  Node LastNode = null;
  boolean remove = false; // flag for deletion
  int inst = 0; float type = 1; // default type
  Frame f;
  Node(float t_, float x_, float y_, float dt_, float type_, Frame f){ t=t_;x=x_;y=y_;dt=dt_; type=type_; this.f=f; }
  void next_node(Node next_node){ NextNode = next_node; }
  void last_node(Node last_node){ LastNode = last_node; }
  
  int general_value(Lens LENS){ // evaluates the nodes type in relation to a given lens, in the Generic mode
    if( !LENS.f.TIME.within(t) ){return 0;} // greyed out, since out of reference frame
    if(LENS.inside(x,y)){return 1;} // white, since in the box
    boolean BEF = LastNode!=null && LENS.inside(LastNode.x, LastNode.y), AFT = NextNode!=null && LENS.inside(NextNode.x, NextNode.y);
    if(BEF & AFT){return 2;}
    if(BEF){return 4;}
    if(AFT){return 3;}
    return 0; // greyed out, since no interesting behaviour with this lens
  }
  int alternate_value(Lens LENS){ // evaluates the nodes type in relation to a given lens, in the Alternate mode
    if( !LENS.f.TIME.within(t) ){return 0;} // greyed out, since out of reference frame
    if( LENS.inside(x,y) ){return 1;} // white, since in the box
    float forward = ALT_TIME_WINDOW; float backward = ALT_TIME_WINDOW; 
    Node cur = this;
    while(cur!= null && abs(this.t - cur.t) < ALT_TIME_WINDOW && !LENS.inside(cur.x, cur.y)){ cur = cur.NextNode; }
    if(cur!= null && abs(this.t - cur.t) < ALT_TIME_WINDOW){forward = abs(cur.t-this.t);}
    cur = this;
    while(cur!= null && abs(this.t - cur.t) < ALT_TIME_WINDOW && !LENS.inside(cur.x, cur.y)){ cur = cur.LastNode; }
    if(cur!= null && abs(this.t - cur.t) < ALT_TIME_WINDOW){backward = abs(cur.t-this.t);}
    if(forward + backward < ALT_TIME_WINDOW){return 2;}
    if(forward < backward){return 3;}
    if(forward > backward){return 4;}
    return 0;
  }
  
  void draw_self(int inst, int symbol){
    if(inst==0){return;}//greyed out
    else if(inst==1){f.base.fill(white(100));f.base.stroke(white(100));}//white
    else if(inst==2){f.base.fill(o2(80));f.base.stroke(o2(80));}//orange
    else if(inst==3){f.base.fill(o1(80));f.base.stroke(o1(80));}//red
    else if(inst==4){f.base.fill(o3(80));f.base.stroke(o3(80));}//green
    
    if(INTERLACE & ALTERNATE & symbol==1){//draw in the interlacing lines
      f.base.strokeWeight(2);
      if( (inst==3 || inst==2) && NextNode!=null ){ f.base.line(x,y,NextNode.x,NextNode.y); }
      if( (inst==4 || inst==2) && LastNode!=null ){ f.base.line(x,y,LastNode.x,LastNode.y); }
    }
    float s = SIZE;
    if(USE_SIZE){s = s*sqrt(dt);}
    if(inst==1){s = s*R;}
    if(symbol==0){ f.base.noStroke();f.base.ellipse(x,y,s,s); }
    else if(symbol==1){ // draws an X symbol
      f.base.strokeWeight(3); f.base.strokeCap(SQUARE);
      f.base.line(x-s/2, y-s/2, x+s/2, y+s/2);
      f.base.line(x-s/2, y+s/2, x+s/2, y-s/2);
    }
  }
  void time_mark(int inst, int level, int num_levels){
    strokeWeight(1);
    if(inst==0){stroke(grey(50));fill(grey(50));}//greyed out
    else if(inst==1){fill(white(100));stroke(white(100));}//white
    else if(inst==2){fill(o2(80));stroke(o2(80));}//orange
    else if(inst==3){fill(o1(80));stroke(o1(80));}//red
    else if(inst==4){fill(o3(80));stroke(o3(80));}//green
    
    float vt = (t-f.TIME.Tmin)*TWIDTH/(f.TIME.Tmax - f.TIME.Tmin);
    float t_width = dt*TWIDTH/(f.TIME.Tmax - f.TIME.Tmin);
    float lower = height-100 + (60.0*level)/num_levels;
    float upper = lower + 60.0/num_levels;
    if(t_width < 2){ line( vt+TBUFFER, lower,  vt+TBUFFER, upper); } // draw timemark as a line
    else{ noStroke(); rect( vt+TBUFFER, lower,  t_width, 60.0/num_levels ); } // draw timemark as a rect, since it is wide
    //if( TIME_PAIRS && level==FILTER.selected && inst>1 ){ line( vt+100, lower, this.x, this.y ); } // connects timemark and onscreen node
  }
  void t_animate_mark(int inst, int symbol, float level, float at, int BINS){
    if(inst==0){return;}//greyed out
    else if(inst==1){f.base.fill(white(100));f.base.stroke(white(100));}//white
    else if(inst==2){f.base.fill(o2(80));f.base.stroke(o2(80));}//orange
    else if(inst==3){f.base.fill(o1(80));f.base.stroke(o1(80));}//red
    else if(inst==4){f.base.fill(o3(80));f.base.stroke(o3(80));}//green
    
    float td = (t-f.TIME.Tmin)/(f.TIME.Tmax-f.TIME.Tmin);
    int bin = floor(BINS*td); println(bin, td, f.TIME.Tmin, t, f.TIME.Tmax);
    float tx = TBUFFER + (bin+0.5)/BINS*TWIDTH; float ty = f.base.height-10 - level;
    float tt = min(1, max(0, 5*at - 4*td ));
    float ix = tt*tx + (1-tt)*x, iy = tt*ty + (1-tt)*y;
    float s = SIZE;
    if(USE_SIZE){s = s*sqrt(dt);}
    if(inst==1){s = s*R;}
    if(symbol==0){ f.base.noStroke();f.base.ellipse(ix,iy,s,s); }
    else if(symbol==1){ // draws an X symbol
      f.base.strokeWeight(3); f.base.strokeCap(SQUARE);
      f.base.line(ix-s/2, iy-s/2, ix+s/2, iy+s/2);
      f.base.line(ix-s/2, iy+s/2, ix+s/2, iy-s/2);
    }
  }
  void t_animate_mark_2(int inst, int symbol, float at, float tx, float ty){
    if(inst==0){return;}//greyed out
    else if(inst==1){f.base.fill(white(100));f.base.stroke(white(100));}//white
    else if(inst==2){f.base.fill(o2(80));f.base.stroke(o2(80));}//orange
    else if(inst==3){f.base.fill(o1(80));f.base.stroke(o1(80));}//red
    else if(inst==4){f.base.fill(o3(80));f.base.stroke(o3(80));}//green
    
    float td = (t-f.TIME.Tmin)/(f.TIME.Tmax-f.TIME.Tmin);
    Lens l = f.FILTER.Lenses.get(f.FILTER.selected);
    
    float tt = min(1, max(0, at ));
    float ix = tt*tx + (1-tt)*x, iy = tt*ty + (1-tt)*y;
    float s = SIZE;
    if(USE_SIZE){s = s*sqrt(dt);}
    if(inst==1){s = s*R;}
    if(symbol==0){ f.base.noStroke();f.base.ellipse(ix,iy,s,s); }
    else if(symbol==1){ // draws an X symbol
      f.base.strokeWeight(3); f.base.strokeCap(SQUARE);
      f.base.line(ix-s/2, iy-s/2, ix+s/2, iy+s/2);
      f.base.line(ix-s/2, iy+s/2, ix+s/2, iy-s/2);
    }
  }
}
