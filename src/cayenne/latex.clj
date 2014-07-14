(ns cayenne.latex
  (:require [clojure.string :as string]))

;; implements some transforms from unicode to latex commands

(def char-to-latex
  {\u00c0 "\\`A" ; begin grave
   \u00c8 "\\`E"
   \u00cc "\\`I"
   \u00d2 "\\`O"
   \u00d9 "\\`U"
   \u00e0 "\\`a"
   \u00e8 "\\`e"
   \u00ec "\\`i"
   \u00f2 "\\`o"
   \u00f9 "\\`u"
   \u00c1 "\\'A" ; begin acute
   \u00c9 "\\'E"
   \u00cd "\\'I"
   \u00d3 "\\'O"
   \u00da "\\'U"
   \u00dd "\\'Y"
   \u00e1 "\\'a"
   \u00e9 "\\'e"
   \u00ed "\\'i"
   \u00f3 "\\'o"
   \u00fa "\\'u"
   \u00fd "\\'y"
   \u00c4 "\\\"A" ; begin diaeresis
   \u00cb "\\\"E"
   \u00cf "\\\"I"
   \u00d6 "\\\"O"
   \u00dc "\\\"U"
   \u00e4 "\\\"a"
   \u00eb "\\\"e"
   \u00ef "\\\"i"
   \u00f6 "\\\"o"
   \u00fc "\\\"u"
   \u00c3 "\\~A" ; begin tilde
   \u00d1 "\\~N"
   \u00d5 "\\~O"
   \u00e3 "\\~a"
   \u00f1 "\\~n"
   \u00f5 "\\~o"
   \u016e "\\r{U}" ; begin ring above
   \u016f "\\r{u}"
   \u010c "\\v{C}" ; begin caron
   \u010e "\\v{D}"
   \u011a "\\v{E}"
   \u0147 "\\v{N}"
   \u0158 "\\v{R}"
   \u0160 "\\v{S}"
   \u0164 "\\v{T}"
   \u017d "\\v{Z}"
   \u010d "\\v{c}"
   \u010f "\\v{d}"
   \u011b "\\v{e}"
   \u0148 "\\v{n}"
   \u0159 "\\v{r}"
   \u0161 "\\v{s}"
   \u0165 "\\v{t}"
   \u017e "\\v{z}"
   \# "\\#" ; begin special symbols
   \$ "\\$"
   \% "\\%"
   \& "\\&"
   \\ "\\\\"
   \^ "\\^"
   ;\_ "\\_" ; disabled
   \{ "\\{"
   \} "\\}"
   \~ "\\~"
   \u2019 "'" ; closing single quote
   \u2018 "`" ; opening single quote
   \u00c5 "\\AA" ; begin non-ASCII letters
   \u00c6 "\\AE"
   \u00d8 "\\O"
   \u00e5 "\\aa"
   \u00e6 "\\ae"
   \u00f8 "\\o"
   \u00df "\\ss"
   \u00a9 "$\\textcopyright$"
   \u2026 "$\\textellipsis$"
   \u2014 "$\\textemdash$"
   \u2013 "$\\textendash$"
   \u00ae "$\\textregistered$"
   \u2122 "$\\texttrademark$"
   \u03b1 "$\\alpha$" ; begin greek alphabet
   \u03b2 "$\\beta$"
   \u03b3 "$\\gamma$"
   \u03b4 "$\\delta$"
   \u03b5 "$\\epsilon$"
   \u03b6 "$\\zeta$"
   \u03b7 "$\\eta$"
   \u03b8 "$\\theta$"
   \u03b9 "$\\iota$"
   \u03ba "$\\kappa$"
   \u03bb "$\\lambda$"
   \u03bc "$\\mu$"
   \u03bd "$\\nu$"
   \u03be "$\\xi$"
   \u03bf "$\\omicron$"
   \u03c0 "$\\pi$"
   \u03c1 "$\\rho$"
   \u03c2 "$\\sigma$"
   \u03c3 "$\\tau$"
   \u03c4 "$\\upsilon$"
   \u03c5 "$\\phi$"
   \u03c6 "$\\chi$"
   \u03c7 "$\\psi$"
   \u03c8 "$\\omega$"})
   
(defn ->latex-str [s]
  (string/join (map #(or (char-to-latex %) %) s)))
  