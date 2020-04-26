class LowerLayer{
  PGraphics base;
  PImage bgr = null;
  boolean has_changed = true;
  Frame f;
  LowerLayer(Frame f){base=createGraphics(1700, 900, P2D); this.f=f;}
  
  // builds most of image, all except interface and foreground features that aren't very time-consuming to draw
  void build(){
    float start_t = millis();
    has_changed = false; // do at the start, in case it changes again before we are done
    base.noSmooth();
    base.beginDraw();
    if(z!=1){ // resizes the background image if we have zoomed in
      bgr = f.bgi.copy();
      bgr.resize(floor(z*f.bgi.width),0);
      bgr = bgr.get(floor(min(mouseX,base.width)*(z-1)), floor(min(mouseY,base.height)*(z-1)), base.width, base.height);
      base.background(bgr);
    }
    else{base.background(f.bgi);}
    // draw a darker filter over the background, to make the actual data more clear on top of it
    base.noStroke(); base.fill(0,0,0,200); base.rect(0,0,base.width,base.height);
    // apply zooming for background features
    if(z!=1){base.translate(-mouseX*(z-1), -min(mouseY,height-100)*(z-1));base.scale(z);}
    // shows the background fixation points
    if(SHOW_DOTS){for(Node n : f.nodes){ // does the background nodes
      if(f.TIME.within(n.t)){
        base.fill(c2(20));
        if(n.LastNode != null && n.NextNode != null){
          float dl = sq(n.x - n.LastNode.x) + sq(n.y - n.LastNode.y);
          float dn = sq(n.x - n.NextNode.x) + sq(n.y - n.NextNode.y);
          float db = sq(n.LastNode.x - n.NextNode.x) + sq(n.LastNode.y - n.NextNode.y);
          if(max(dl,dn)< sq(BRIEF_MIN) ){ base.fill(c3(20)); }
          else if( db*sq(GLANCE_STANDARD) < min(dl, dn) ){ base.fill(c1(20)); }
        }
        if(!USE_SIZE){base.ellipse(n.x,n.y,SIZE,SIZE);}else{base.ellipse(n.x,n.y,SIZE*sqrt(n.dt),SIZE*sqrt(n.dt));}
      }
    }}
    // shows the background saccade lines, depending on the filtering
    for(NodeCurve n : f.node_curves){
      if( n.active && n.dist<BRIEF_MIN ){ // draws the short saccades first, so that they can form a background for context
        base.stroke(n.get_color(25)); base.strokeWeight(2);
        base.line(n.x1, n.y1, n.xs[n.xs.length-1], n.ys[n.ys.length-1]);
      }
    }
    if(!ANIMATE){
      for(NodeCurve n : f.node_curves){ // then draws the longer saccades on top of them
        if( n.active && n.dist>=BRIEF_MIN ){
          for(PShape i : n.content){base.shape(i);}
        }
      }
    }
    base.endDraw();
    println("lower layer in ", millis()-start_t);
  }
}
