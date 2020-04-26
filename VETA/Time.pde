class Time{
  float Ts = 0; float Te = 1;
  float TTmin = 0; float TTmax = 0;
  float Tmin = 0; float Tmax = 0;
  ArrayList<Float> markers = new ArrayList<Float>();
  Time(){}
  boolean within(float t){ return (t >= Tmin + Ts*(Tmax-Tmin)) & (t <= Tmin + Te*(Tmax-Tmin));}
  float start(){return Tmin + Ts*(Tmax-Tmin);}
  float end(){return Tmin + Te*(Tmax-Tmin);}
  
  void reset(){Tmin=TTmin; Tmax=TTmax; Ts=0; Te=1;}
  void movie_time_clicked(){MainFrame.movie_update_time = Tmin + ((mouseX-TBUFFER)/TWIDTH) * (Tmax - Tmin);}
  String format_time(float t){return String.format("%01d", floor(t/3600)) +':'+ String.format("%02d", floor((t/60)%60)) +':'+String.format("%02d", floor(t%60));}
  void zoom(){
    float nTmin = Tmin + Ts*(Tmax-Tmin), nTmax = Tmin + Te*(Tmax-Tmin);
    Tmin = nTmin; Tmax = nTmax; Ts=0; Te=1;
  }
  
  void draw(){
    fill(white(100)); stroke(white(100)); strokeWeight(2);
    line(TBUFFER,height-40,TBUFFER+TWIDTH,height-40);
    arc(TBUFFER + Ts*TWIDTH, height-40, 30, 30, HALF_PI, 3*HALF_PI);
    arc(TBUFFER + Te*TWIDTH, height-40, 30, 30, -HALF_PI, HALF_PI);
    float tts = Tmin + Ts*(Tmax-Tmin); float tte = Tmin + Te*(Tmax-Tmin);
    text(format_time(tts), 10 + Ts*TWIDTH, height-10);
    text(format_time(tte), 50 + Te*TWIDTH, height-10);
    // add marker for movie time
    if(MainFrame.clip!=null){
      float tc = MainFrame.clip.time();
      strokeWeight(1);stroke(white(100));
      float x = (tc-Tmin)/(Tmax - Tmin);
      line( x*TWIDTH+TBUFFER, height-20,  x*TWIDTH+TBUFFER, height-60);
    }
    // add user defined marks
    stroke(o1(70));strokeWeight(2);
    for(float i : markers){
      float c = (i-Tmin)/(Tmax - Tmin)*TWIDTH+TBUFFER;
      line(c, height-100, c, height-20);
    }
  }
  void clicked(){
    if(mouseButton==LEFT){ // move the closest time bound
      float q = max(0, min(1, (mouseX - TBUFFER)/TWIDTH));
      if( q < (Ts+Te)/2 ){Ts = q;}else{Te=q;}
    }else if(mouseButton==RIGHT){ // draw or delete a marker
      float clicked_time = Tmin + (Tmax-Tmin)*(mouseX-TBUFFER)/TWIDTH;
      int closest = 0; float mindist = abs(Tmax-Tmin);
      for(int i=0;i<markers.size();i++){
        float newdist = abs(markers.get(i)-clicked_time);;
        if(newdist < mindist){closest=i;mindist=newdist;}
      }
      if(mindist > 0.01*abs(Tmax-Tmin)){ markers.add(clicked_time); }
      else{ markers.remove(closest); }
    }
  }
}
