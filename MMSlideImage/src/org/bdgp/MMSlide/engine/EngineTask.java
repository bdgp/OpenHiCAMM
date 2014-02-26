package org.bdgp.MMSlide.engine;

public interface EngineTask {
   public void requestStop();
   public void requestPause();
   public void requestResume();
   public void run();
}
