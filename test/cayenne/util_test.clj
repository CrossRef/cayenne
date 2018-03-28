(ns cayenne.util-test
  (:require [clojure.test :refer [deftest testing are]]
            [cayenne.util :refer [only-facemark]]))

(deftest only-facemark-test
  (testing "returns original input string when no facemark or xml"
    (are [s expected] (= expected (only-facemark s))
         "No facemark or xml in string" "No facemark or xml in string"
         "Just a plain string" "Just a plain string"))
  
  (testing "returns original string and facemark when no xml"
    (are [s expected] (= expected (only-facemark s))
         "A facemark string with <i>italic</i> and <b>bold</b>" 
         "A facemark string with <i>italic</i> and <b>bold</b>"

         "<u>Underline</u> in a facemark string" 
         "<u>Underline</u> in a facemark string"))

  (testing "returns original string with less than and greater than"
    (are [s expected] (= expected (only-facemark s))
         "A string with < less than character"
         "A string with < less than character"
         
         "A string with > greater than character"
         "A string with > greater than character" 

         "A string with < less than and > characters"
         "A string with < less than and > characters" 

         "A string with <less than and> characters"
         "A string with <less than and> characters" 

         "A string with <lessthanand> characters"
         "A string with <lessthanand> characters" 
         
         "A string with > greater than and < less than characters"
         "A string with > greater than and < less than characters"))
  
  (testing "returns only original string and facemark when input contains xml"
    (are [s expected] (= expected (only-facemark s))
         "A <unexpected-xml>Some Data</unexpected-xml> facemark string with <i>italic</i>, <b>bold</b>, <u>underline</u>" 
         "A facemark string with <i>italic</i>, <b>bold</b>, <u>underline</u>"

         "A <unexpected-xml>Some Data</unexpected-xml> facemark string with
          a new line, <i>italic</i>, <b>bold</b>, <u>underline</u>" 
         "A facemark string with a new line, <i>italic</i>, <b>bold</b>, <u>underline</u>"

         "<ovl>Overline</ovl> in a facemark string"
         "<ovl>Overline</ovl> in a facemark string"

         "<ovl>Overline</ovl> in a facemark string"
         "<ovl>Overline</ovl> in a facemark string"

         "<sup>Superscript</sup> in a facemark string"
         "<sup>Superscript</sup> in a facemark string"

         "<sub>Subscript</sub> in a facemark string"
         "<sub>Subscript</sub> in a facemark string"

         "<scp>Smallcaps</scp> in a facemark string"
         "<scp>Smallcaps</scp> in a facemark string"

         "<tt>Typewriter</tt> in a facemark string"
         "<tt>Typewriter</tt> in a facemark string"

         " <b>A line that needs trimming at the start"
         "<b>A line that needs trimming at the start"

         "<b>A line that needs trimming at the end "
         "<b>A line that needs trimming at the end"

         "<u>Underline</u> in a facemark string" 
         "<u>Underline</u> in a facemark string")))
