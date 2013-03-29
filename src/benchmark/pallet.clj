(ns benchmark.pallet    
  (:require [pallet.action.package]
            [pallet.core]
            [pallet.compute]
            [pallet.configure]
            [pallet.stevedore.bash])
  (:use [pallet.core :only [group-spec server-spec node-spec]]
        [pallet.crate.automated-admin-user :only [automated-admin-user]]
        [pallet.stevedore :only [script with-script-language]]
        [pallet.script :only [with-script-context]]
        [pallet.phase :only [phase-fn]]))


(pallet.core/converge
  (pallet.core/group-spec "cx-ocho"
   :count 1
   :node-spec (pallet.core/node-spec
               :hardware {:min-cores 2 :min-ram 2048}
               :network {:inbound-ports [22 80]}
               :image {:os-family :ubuntu :image-id "us-east-1/ami-3c994355"})            
   :phases {:bootstrap automated-admin-user})
  :compute (pallet.configure/compute-service :aws))

 
(with-script-language :pallet.stevedore.bash/bash
  (with-script-context [:ubuntu]
    (script
      ("wget https://s3.amazonaws.com/cx-jdk/jdk-7u17-linux-x64.gz")
      ("tar xzf jdk-7u17-linux-x64.gz")
      ("sudo update-alternatives --install \"/usr/bin/java\" \"java\" \"/home/mcohen3/jdk1.7.0_17/bin/java\" 1")
      ("sudo update-alternatives --install \"/usr/bin/javac\" \"javac\" \"/home/mcohen3/jdk1.7.0_17/bin/javac\" 1")
      ("sudo update-alternatives --install \"/usr/bin/javaws\" \"javaws\" \"/home/mcohen3/jdk1.7.0_17/bin/javaws\" 1")
      ("sudo apt-get install git")
      ("git clone git://github.com/mcohen01/amazonica-benchmark.git")
      ("cd amazonica-benchmark")
      ("\"access-key secret-key\" >> aws.config")))


(pallet.core/converge
  (pallet.core/group-spec "cx-ocho" :count 0)
  :compute (pallet.configure/compute-service :aws))