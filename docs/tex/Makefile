UV := uv run
LATEX := lualatex
LATEX_OPT := -interaction=nonstopmode -halt-on-error

.PHONY: all charts clean clean-all

all: charts pitch.pdf

charts:
	@echo "Generating charts..."
	@$(UV) python generate_charts.py
	@echo "Charts done."

pitch.pdf: pitch.tex charts
	@echo "Compiling LaTeX (pass 1)..."
	@$(LATEX) $(LATEX_OPT) pitch.tex > /dev/null 2>&1 || \
		($(LATEX) $(LATEX_OPT) pitch.tex 2>&1 | head -40 && false)
	@echo "Compiling LaTeX (pass 2)..."
	@$(LATEX) $(LATEX_OPT) pitch.tex > /dev/null 2>&1 || true
	@echo "Done — pitch.pdf"

clean:
	rm -f *.aux *.log *.nav *.out *.snm *.toc *.vrb *.fdb_latexmk *.fls *.synctex.gz
	rm -f charts/*.png

clean-all: clean
	rm -rf .venv
