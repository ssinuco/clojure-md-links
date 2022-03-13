(ns mdlinks.core
  (:require 
   [clojure.tools.cli :refer [parse-opts]]
   [clojure.string :as string]
   [clojure.java.io :refer [file]]
   [clj-http.client :as client :refer [get] :rename {get http-get}]
   [slingshot.slingshot :refer [try+]])
  (:import [org.commonmark.parser Parser]
           [org.commonmark.node Node Link AbstractVisitor]
           [mdlinks.visitors LinkVisitor])
  (:gen-class))

(defn get-folder-files-map
  "Given a path (File Java Object), returns a list of files in the path and in its subdirectories"
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
  "Given a seq of paths (File Java Object), returns a list of files in the paths and in their subdirectories"
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
  "Returns a promise which is resolved with a vec of links in a markdown file"
  [filePath]
  (let [p (promise)]    
    (future 
      (let [parser (.build (Parser/builder))
            visitor (LinkVisitor.)
            content (slurp filePath)]
        (let [parsed (.parse parser content)]
          (.accept parsed visitor)
          (let [fileLinks (.getLinks visitor)]
            (deliver p (into [] (seq fileLinks)))))))
    p))

(defn process-folder
  "Returns a promise wich is resolved with a vector of links in all mardown files of a directory"
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
  "Validates a url making a http request"
  [link]
  (let [p (promise)
        url (:href link)]
    (future
      (try+
        (let [response (http-get url {:cookie-policy :standard})]
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
  "Given a vector of URLs, validates each item"
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
  "Extracts and validate all links of allmardown files in a directory and its subdirectories"
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

(defn usage 
  "Returns a help message about how to use the command"
  [options-summary]
  (->> ["MD-Links: Extracts links from markdown files."
        ""
        "Usage: mdlinks <path-to-file> [options]"
        ""
        "Options:"
        options-summary]
       (string/join \newline)))

(defn error-msg 
  "Returns a error message "
  [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(def cli-options
  [[nil "--validate" "Validate urls" :default false] 
   [nil "--stats" "Calculate stats" :default false]])
 
(defn validate-args
  "Validate arguments of the command"
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

(defn exit 
  "Exits scripts with a message"
  [status msg]
  (println msg)
  (System/exit status))

(defn -main
  [& args]
  (let [{:keys [path options exit-message ok?]} (validate-args args)]
    (if exit-message
      (exit 1 exit-message)
      (println @(md-links path options)))
    (shutdown-agents)))
