NAME=cover proposal
USE_PDFLATEX=1

proposal_TEXSRCS=proposal.tex
proposal_BIBTEXSRCS=proposal.bib
cover_TEXSRCS=cover.tex

OTHER_PDF=$(wildcard fig/*.pdf)
OTHER+=$(OTHER_PDF)

ifeq ($(shell uname), Linux)
  VIEWPDF=evince
  include /usr/share/latex-mk/latex.gmk
else
  # OSX
  VIEWPDF=open
  include /opt/local/share/latex-mk/latex.gmk
endif
