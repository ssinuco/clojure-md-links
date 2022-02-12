(ns mdlinks.visitors.LinkVisitor
  (:import [org.commonmark.node Link Document]
           [java.util ArrayList])
  (:gen-class
   :state state
   :init init
   :name mdlinks.visitors.LinkVisitor
   :extends org.commonmark.node.AbstractVisitor
   :methods [[getLinks [] java.util.ArrayList]]))

(defn -init []
  [[] (ArrayList.)])

(comment 
  (defn -visit-Link
    [this ^Link link]
    (.visitChildren this link)
    (let [linkObj (java.util.HashMap.)]
      (.put linkObj :title (.getTitle link))
      (.put linkObj :destination (.getDestination link))
      (.add (.state this) linkObj))
    (comment 
      (.add (.state this) (.getDestination link)))))

(defn -visit-Link
  [this ^Link link]
  (.visitChildren this link)
  (.add 
   (.state this) 
   {
    :title (.getTitle link)
    :destination (.getDestination link)
    }))

(defn -getLinks
  [this]
  (.state this))

