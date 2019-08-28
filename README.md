# Concrete Ingesters ACE Event Extraction
A more complete version of Xiao Liu's concrete-ingesters-acex3 java toolkit, which was originally modified from jhu's [concrete-ingesters-acere](https://www.mvnrepository.com/artifact/edu.jhu.hlt/concrete-ingesters-acere).

Maven:
```xml
<dependency>
    <groupId>io.github.lx865712528</groupId>
    <artifactId>concrete-ingesters-acex3</artifactId>
    <version>2.3.0</version>
</dependency>
```

Specially for time2norm data splits.

This version will parse each trigger as an argument with "TRIGGER".

All values and times are extracted at the same time.

# Specifications to run this code:
mvn clean & mvn install should install all java tools dependencies. To run the main class in edu.bit.nlp.concrete.ingesters.acex3AceApf2Concrete, pass in the following arguments:
* Root directory of the remaining file/directory paths
* Directory where json files are to be saved
* Directory of English LDC2006T06 distribution
* Directory of training/dev/test split files

Please put apf.v5.1.1.dtd in the same directory as every APF/SGM.

## Paper Citation:

```bibtex
@inproceedings{DBLP:conf/emnlp/LiuLH18,
  author    = {Xiao Liu and
               Zhunchen Luo and
               Heyan Huang},
  title     = {Jointly Multiple Events Extraction via Attention-based Graph Information
               Aggregation},
  booktitle = {Proceedings of the 2018 Conference on Empirical Methods in Natural
               Language Processing, Brussels, Belgium, October 31 - November 4, 2018},
  pages     = {1247--1256},
  year      = {2018},
  crossref  = {DBLP:conf/emnlp/2018},
  url       = {https://aclanthology.info/papers/D18-1156/d18-1156},
  timestamp = {Sat, 27 Oct 2018 20:04:50 +0200},
  biburl    = {https://dblp.org/rec/bib/conf/emnlp/LiuLH18},
  bibsource = {dblp computer science bibliography, https://dblp.org}
}
```
