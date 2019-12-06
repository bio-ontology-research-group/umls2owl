@Grab(group='net.sourceforge.owlapi', module='owlapi-distribution', version='4.5.13')
@Grab(group='org.semanticweb.elk', module='elk-owlapi', version='0.4.3')


import org.apache.commons.cli.Option
import org.semanticweb.owlapi.io.* 
import org.semanticweb.owlapi.model.*
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary
import org.semanticweb.owlapi.apibinding.OWLManager


def cli = new CliBuilder()
cli.with {
usage: 'Self'
  h longOpt:'help', 'this information'
  u longOpt:'umls-directory', 'path to the UMLS MetaThesaurus directory', args:1, required:true
  s longOpt:'include-umls-subsets', 'UMLS subsets to include (comma separated)', args:1, required: false
  l longOpt:'list-umls-subsets', 'lists available UMLS subsets', required: false
  o longOpt:'output-file', 'Output file containing the selected UMLS subsets in OWL', args:1, required:false
  r longOpt:'make-rdf-output', 'Format the output as RDF (default is OWL)', required: false
}
def opt = cli.parse(args)
if( !opt ) {
  //  cli.usage()
  return
}

if (opt.h) {
  cli.usage()
  return
}

def subsets = new TreeSet()
if (opt.s) {
  opt.s.split(",").each { subsets.add(it) }
}

def umlsdir = opt.u
/* finding subsets */
def flag = false
def map = [:]
new File(umlsdir+"/MRSAB.RRF").splitEachLine("\\|") { line ->
  def id = line[3]
  def name = line[4]
  map[id] = name
}

if (opt.l) {
  println "Subset ID\tSubset name"
  map.each { k, v ->
    println "$k\t$v"
  }
  System.exit(0)
}

def onturi = "http://phenomebrowser.net/umls#"

OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
OWLDataFactory factory = manager.getOWLDataFactory()
OWLOntology ontology = manager.createOntology(IRI.create(onturi))

OWLAxiom ax = null

def addAnno = {resource, prop, cont ->
  OWLAnnotation anno = factory.getOWLAnnotation(
    factory.getOWLAnnotationProperty(prop.getIRI()),
    factory.getOWLTypedLiteral(cont))
  def axiom = factory.getOWLAnnotationAssertionAxiom(resource.getIRI(),
						     anno)
  manager.addAxiom(ontology,axiom)
}

def outfile = new File(opt.o)
def outfilename = outfile.getCanonicalPath()

def fout = null
if (opt.r) {
  fout = new PrintWriter(new BufferedWriter(new FileWriter(outfilename)))
  fout.println ("@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n\n")
}



println "Parsing labels and synomyms"
new File(umlsdir+"MRCONSO.RRF").splitEachLine("\\|") { line ->
  def uid = line[0]
  def lang = line[1]
  def type = line[2]
  def sourceid = line[13]
  def subset = line[11]
  def label = line[14].replaceAll("\\p{C}", "?")
  if (!opt.s || subset in subsets) {
    def cl = factory.getOWLClass(IRI.create(onturi+uid))
    if (opt.r) {
      fout.println(cl.toString()+" <"+OWLRDFVocabulary.RDFS_LABEL+"> "+factory.getOWLTypedLiteral(label).toString()+" .")
      //    if (type == "P") {
    } else {
      addAnno(cl, OWLRDFVocabulary.RDFS_LABEL, label)
      //    } else {
      //      addAnno(cl, OWLRDFVocabulary.RDFS_LABEL, label)
    }
  }
}

println "Parsing definitions"
new File(umlsdir+"MRDEF.RRF").splitEachLine("\\|") { line ->
  def uid = line[0]
  def definition = line[5]
  def subset = line[4]
  if (!opt.s || subset in subsets) {
    def cl = factory.getOWLClass(IRI.create(onturi+uid))
    if (opt.r) {
      fout.println(cl.toString()+" <"+OWLRDFVocabulary.RDFS_COMMENT+"> "+factory.getOWLTypedLiteral(definition).toString()+" .")
    } else {
      addAnno(cl, OWLRDFVocabulary.RDFS_COMMENT, definition)
    }
  }
}

println "Parsing relations"
new File(umlsdir+"MRREL.RRF").splitEachLine("\\|") { line ->
  def id1 = line[0]
  def id2 = line[4]
  def rel = line[7]
  def subset = line[10]
  def reltype = line[3]
  def direction = line[3]
  if (!opt.s || subset in subsets) {
    if (rel) {
      def cl1 = factory.getOWLClass(IRI.create(onturi+id1))
      def cl2 = factory.getOWLClass(IRI.create(onturi+id2))
      if (rel =="isa") { // use OWL subclassing instead of relationship; cl2 is a subclass of cl1
	if (opt.r) {
	  fout.println(cl2.toString()+" <"+OWLRDFVocabulary.RDFS_SUBCLASS_OF+"> "+cl1.toString()+" .")
	} else {
	  ax = factory.getOWLSubClassOfAxiom(cl2,cl1)
	  manager.addAxiom(ontology,ax)
	}
      } else if (rel=="inverse_isa") { // use OWL subclassing instead of relationship; cl1 is a subclass of cl2
	if (opt.r) {
	  fout.println(cl1.toString()+" <"+OWLRDFVocabulary.RDFS_SUBCLASS_OF+"> "+cl2.toString()+" .")
	} else {
	  ax = factory.getOWLSubClassOfAxiom(cl1,cl2)
	  manager.addAxiom(ontology,ax)
	}
      } else if (reltype == "RO") { // other kind of relation
	OWLObjectProperty prop = factory.getOWLObjectProperty(IRI.create(onturi+rel))
if (opt.r) {
fout.println(cl1.toString()+" "+prop.toString()+" "+cl2.toString()+" .")
	} else {
ax = factory.getOWLSubClassOfAxiom(cl2, factory.getOWLObjectSomeValuesFrom(prop,cl1))
	  manager.addAxiom(ontology,ax)
	}
      }
    }
  }
}

//manager.saveOntology(ontology, new OWLFunctionalSyntaxOntologyFormat(), IRI.create("file:"+outfilename))
if (opt.r) {
  fout.flush()
  fout.close()
} else {
  manager.saveOntology(ontology, IRI.create("file:"+outfilename))
}

