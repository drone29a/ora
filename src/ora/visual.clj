(ns ora.visual
  (:require [clojure.core.async :as a
             :refer [>! <! >!! <!! go chan buffer
                     close! thread alts! alts!! timeout]]
            [quil.core :as q]
            [quil.middleware :as qm]))

(set! *warn-on-reflection* true)

(defn setup
  []
  {})
