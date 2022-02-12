(ns mdlinks.core
  (:require 
   [clojure.tools.cli :refer [parse-opts]]
   [clojure.string :as string])
  (:import [org.commonmark.parser Parser]
           [org.commonmark.node Node Link AbstractVisitor]
           [mdlinks.visitors LinkVisitor])
  (:gen-class))

(use '[clojure.java.io :only (file)])
(use '[clj-http.client :as client])
(use '[slingshot.slingshot :only [try+]])

(defn get-folder-files-map
  [parent]
  (if (.isDirectory parent)     
    (let [children (.listFiles parent)]
      (flatten 
       (map 
        (fn
          [child]
          (get-folder-files-map child))
        children)))
    [parent]))

(defn get-folder-files
  [queue]
  (if (not-empty queue)
    (let [firstItem (first queue)]
      (if (.isDirectory firstItem)        
        (get-folder-files 
         (concat 
          (rest queue) 
          (seq (.listFiles firstItem))))
        (conj 
         (get-folder-files (rest queue)) 
         firstItem)))
    (seq [])))

(defn process-file
  [filePath]
  (let [p (promise)]    
    (future 
      (Thread/sleep 5000)
      (let [parser (.build (Parser/builder))
            visitor (LinkVisitor.)
            content (slurp filePath)]
        (let [parsed (.parse parser content)]
          (.accept parsed visitor)
          (let [fileLinks (.getLinks visitor)]
            (deliver p (into [] (seq fileLinks)))))))
    p))

(defn process-folder
  [folderPath]
  (let [p (promise)]
    (future
      (let [folderSeq (seq [(file folderPath)])]        
        (let [promises 
              (mapv 
               #(process-file %) 
               (get-folder-files folderSeq))]
          (let [links 
                (mapv (fn [p] @p) promises)]
            (deliver p links)))))
    p))

(defn send-http-request
  [link]
  (let [p (promise)
        url (:href link)]
    (future
      (try+
        (let [response (client/get url)]
          (deliver p 
                   (assoc 
                    link 
                    :status (:status response) :ok "ok")))
        (catch Object {:keys [status]}
          (deliver p 
                   (assoc 
                    link 
                    :status status :ok "fail")))))
    p))

(defn validate-links
  [links]
  (let [p (promise)]
    (future
      (let [promises 
              (mapv 
               #(send-http-request %) 
               links)]
          (let [responses 
                (mapv (fn [p] @p) promises)]
            (deliver p responses))))
    p))

(defn md-links
  [path options]
  (let [
        validate (:validate options)
        stats (:stats options)
        links (flatten @(process-folder path))]
    (if validate
      (validate-links links)
      (let [p (promise)]
        (deliver p links)
        p))))

(defn usage [options-summary]
  (->> ["MD-Links: Extracts links from markdown files."
        ""
        "Usage: mdlinks <path-to-file> [options]"
        ""
        "Options:"
        options-summary]
       (string/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(def cli-options
  [[nil "--validate" "Validate urls" :default false]
   [nil "--stats" "Calculate stats" :default false]])
 
(defn validate-args
  [args]
  (let [{:keys [options arguments errors summary]} 
        (parse-opts args cli-options)]
    (cond
      ;; errors
      errors
      {:exit-message (error-msg errors)}
      ;; path is mandatory
      (= 1 (count arguments))
      {:path (first arguments) :options options}
      ;; fail
      :else
      {:exit-message (usage summary)}
      )))


(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn -main
  [& args]
  (let [{:keys [path options exit-message ok?]} (validate-args args)]
    (if exit-message
      (exit 1 exit-message)
      (println @(md-links path options)))))
