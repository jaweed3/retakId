UV := uv run
LATEX := tectonic
PUML := plantuml

.PHONY: all diagrams charts clean clean-all

all: diagrams charts paper.pdf pitch.pdf

diagrams:
	@echo "Generating PUML diagrams..."
	@$(PUML) -tpng -charset UTF-8 diagrams/*.puml
	@echo "Diagrams done."

charts:
	@echo "Generating charts..."
	@$(UV) python generate_charts.py
	@echo "Charts done."

paper.pdf: paper.tex diagrams
	@echo "Compiling paper.tex..."
	@$(LATEX) paper.tex
	@echo "Done — paper.pdf"

pitch.pdf: pitch.tex charts
	@echo "Compiling pitch.tex..."
	@$(LATEX) pitch.tex
	@echo "Done — pitch.pdf"

clean:
	rm -f *.aux *.log *.nav *.out *.snm *.toc *.vrb *.fdb_latexmk *.fls *.synctex.gz *.bbl *.blg
	rm -f charts/*.png

clean-all: clean
	rm -rf .venv
	rm -f diagrams/*.png
