(ns ora.core
  (:import [javax.sound.sampled
            TargetDataLine
            DataLine
            DataLine$Info
            AudioFormat
            AudioSystem])
  (:gen-class))

(set! *warn-on-reflection* true)

(defn create-audio-format
  ^AudioFormat
  []
  (AudioFormat. 44100
                16
                1
                true
                false))

(defn create-data-line-info
  ^DataLine$Info
  [audio-format]
  (DataLine$Info. TargetDataLine audio-format))

(defn get-target-data-line
  ^TargetDataLine
  [^DataLine$Info info]
  (AudioSystem/getLine info))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))
