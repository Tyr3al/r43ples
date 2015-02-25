#!/bin/bash

# ontology

bunzip2 dbpedia_3.9.owl.bz2


# instance data (3.7 GB compressed; 38.4 GB; 260 mil triples )
wget http://live.dbpedia.org/dumps/dbpedia_2013_07_18.nt.bz2
bunzip2 dbpedia_2013_07_18.nt.bz2
split --number=l/100 -d dbpedia_2013_07_18.nt dbpedia_2013_07_18.split.
# @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
# @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
# @prefix owl: <http://www.w3.org/2002/07/owl#> .
# @prefix void: <http://rdfs.org/ns/void#> .
# 
# @prefix this: <> .
# 
# this:Dataset a void:Dataset ; 
#  rdfs:seeAlso <http://dbpedia.org> ; 
#  rdfs:label "dbpedia" ; 
#  void:sparqlEndpoint <http://localhost:8890/sparql> ; 
#  void:triples 260145582 ; 
#  void:classes 448 ; 
#  void:entities 4561253 ; 
#  void:distinctSubjects 27391063 ; 
#  void:properties 73940 ; 
#  void:distinctObjects 103060313 . 


# download changesets
wget -r -np -nd -nH -L -e robots=off -A "gz, html" http://live.dbpedia.org/changesets/2013/11/04/13/
gunzip *.nt.gz
