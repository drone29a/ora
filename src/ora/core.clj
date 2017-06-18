(ns ora.core
  (:require [ora.line :as l]
            [clojure.core.async :as a
             :refer [>! <! >!! <!! go chan buffer
                     close! thread alts! alts!! timeout]])
  (:gen-class))

(set! *warn-on-reflection* true)

(defn signal-val
  [lo-byte
   hi-byte]
  (comment (Float/intBitsToFloat (bit-and 0x0000ffff
                                          (bit-or (bit-and lo-byte 0xff)
                                                  (bit-shift-left (bit-and hi-byte 0xff) 8)))))
  (comment (-> (bit-or (bit-and lo-byte 0xff)
                       (bit-shift-left (bit-and hi-byte 0xff) 8))
               ;;(/ (double Short/MAX_VALUE))
               ;;short
               float))
  ;; Not masking high byte seems to be correct. Seeing wavvy values
  (-> (bit-or (bit-and lo-byte 0xff)
              (bit-shift-left hi-byte 8))
               ;;(/ (double Short/MAX_VALUE))
               ;;short
               float))

(defn -main
  "Do things."
  [& args]
  (let [line-in (l/start-capture-line-in)
        in (:out line-in)]
    (a/go-loop [lo-byte (<! in)
                hi-byte (<! in)]
      (let [val (signal-val lo-byte hi-byte)]
        (println lo-byte hi-byte val))
      (recur (<! in) (<! in)))))
