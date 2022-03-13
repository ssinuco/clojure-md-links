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

(defn -visit-Link
  [this ^Link link]
  (.visitChildren this link)
  (.add 
   (.state this) 
   {
    :text (.getLiteral (.getFirstChild link))
    :href (.getDestination link)
    }))

(defn -getLinks
  [this]
  (.state this))

