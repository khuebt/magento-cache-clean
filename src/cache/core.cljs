(ns cache.core
  (:require [cache.cache :as cache]
            [magento.app :as mage]
            [file.system :as fs]
            [log.log :as log]
            [magento.watcher :as watcher]))

(set! *warn-on-infer* true)

(defn- exit-with-code [code]
  (let [proc ^js/process (js/require "process")]
    (.exit proc code)))

(defn has-switch?
  "Return true is one of the given opts strings is contained within the args seq"
  [switches args]
  (let [matches-switch (set (if (string? switches) #{switches} switches))]
    (reduce (fn [x arg]
              (when (matches-switch arg) (reduced true))) nil args)))

(defn find-arg
  "If args contains one of the given argument switches, returns the next arg in
  the seq, otherwise returns nil."
  [flags args]
  (let [matches-flag (set (if (string? flags) #{flags} flags))]
    (let [r (reduce (fn [x arg]
                      (cond
                        (matches-flag arg) true
                        x (reduced arg))) nil args)]
      (if (= true r) nil r))))

(defn find-basedir-in-path [dir]
  (let [dir (fs/realpath dir)]
    (if (fs/exists? (str dir "/app/etc/env.php")) dir
        (let [parent (fs/dirname dir)]
          (when (not= parent dir)
            (recur parent))))))

(defn find-basedir-in-args [args]
  (find-arg ["-d" "--directory"] args))

(defn find-basedir [args]
  (let [basedir (or (find-basedir-in-args args) (find-basedir-in-path (fs/cwd)))]
    (when-not basedir
      (throw (ex-info "Unable to determine the Magento directory" {})))
    (when-not (fs/dir? basedir)
      (throw (ex-info (str "The Magento directory \"" basedir "\" does not exist") {})))
    (log/info "Magento dir" basedir)
    basedir))

(defn find-log-level [args]
  (letfn [(arg->verbosity [arg]
            (case arg
              ( "-v" "--verbose") 1
              ("-vv" "--debug")   2
              ("-s" "--silent")  -1
              0))]
    (reduce + 1 (map arg->verbosity args))))

(defn help-the-needfull []
  (println "Usage: cache-clean.js [options and flags] [cache-types...]
Clean the given cache types. If none are given, clean all cache types.

--directory|-d <dir>    Magento base directory
--watch|-w              Watch for file changes
--verbose|-v            Display more information
--debug|-vv             Display too much information
--silent|-s             Display less information
--help|-h               This help message"))

(defn help-needed? [args]
  (has-switch? ["-h" "--help"] args))

(defn arg-with-val? [arg]
  (#{"--directory" "-d"} arg))

(defn switch? [arg]
  (#{"--watch" "-w"
     "--verbose" "-v"
     "--debug" "-vv"
     "--silent" "-s"
     "--help" "-h" } arg))

(defn remove-switches-and-args-with-vals [args]
  (let [args (vec args)]
    (loop [xs [] i 0]
      (if (= i (count args)) xs
          (let [arg (get args i)]
            (cond
              (arg-with-val? arg) (recur xs (+ i 2))
              (switch? arg) (recur xs (inc i))
              :else (recur (conj xs arg) (inc i))))))))

(defn init-app [args]
  (log/set-verbosity! (find-log-level args))
  (mage/set-base-dir! (find-basedir args)))

(defn clean-cache-types [args]
  (let [cache-types (remove-switches-and-args-with-vals args)]
    (cache/clean-cache-types cache-types)))

(defn process [args]
  (init-app args)
  (if (has-switch? ["-w" "--watch"] args)
    (watcher/start)
    (clean-cache-types args)))

(defn -main [& args]
  (log/always "Sponsored by https://www.mage2.tv\n")
  (if (help-needed? args)
    (help-the-needfull)
    (try (process args)
         (catch :default ^Error e
           (binding [*print-fn* *print-err-fn*]
             (println "[ERROR]" (or (.-message e) e)))
           (exit-with-code 1)))))

(set! *main-cli-fn* -main)
