"""
Shrinks the Material Symbols font to the icons the application uses (icons.txt, one name per line).

The full font (~460 KB) draws thousands of icons; a page uses about a hundred. The icons are ligatures: the word
"receipt_long" is drawn as one glyph. Only the ligatures spelling a name of icons.txt are kept, then the font is
cut down to the glyphs they reach. The FILL axis (.icon-fill) is kept.

Needs fontTools and brotli (pip install fonttools brotli). From the project folder, after adding an icon to a
template, add its name to icons.txt and run (IconFontTest checks that the list covers the templates):

    python src/main/frontend/icons/subset-icons.py

The complete font is kept next to this script (material-symbols-outlined-full.woff2), it is not served.
"""
import sys
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont


def ligature_subtables(font):
    for lookup in font['GSUB'].table.LookupList.Lookup:
        for table in lookup.SubTable:
            inner = table.ExtSubTable if lookup.LookupType == 7 else table
            if getattr(inner, 'LookupType', lookup.LookupType) == 4 and hasattr(inner, 'ligatures'):
                yield inner


def main(source, target):
    names = {line.strip() for line in (Path(__file__).parent / 'icons.txt').read_text().splitlines() if line.strip()}
    font = TTFont(source)
    char_of = {glyph: chr(code) for code, glyph in font.getBestCmap().items()}
    found = set()
    for table in ligature_subtables(font):
        for first, ligatures in list(table.ligatures.items()):
            kept = []
            for ligature in ligatures:
                word = ''.join(char_of.get(glyph, '\0') for glyph in [first, *ligature.Component])
                if word in names:
                    kept.append(ligature)
                    found.add(word)
            if kept:
                table.ligatures[first] = kept
            else:
                del table.ligatures[first]
    missing = sorted(names - found)
    if missing:
        sys.exit('Not in the font: ' + ', '.join(missing))

    options = subset.Options()
    options.flavor = 'woff2'
    options.layout_features = ['*']
    options.notdef_outline = True
    options.name_IDs = ['*']
    subsetter = subset.Subsetter(options)
    subsetter.populate(text=''.join(sorted({c for name in names for c in name})))
    subsetter.subset(font)
    font.flavor = 'woff2'
    font.save(target)
    print(f'{len(names)} icons, {Path(target).stat().st_size // 1024} KB')


HERE = Path(__file__).parent

if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else HERE / 'material-symbols-outlined-full.woff2',
         sys.argv[2] if len(sys.argv) > 2 else HERE.parents[1] / 'resources/static/fonts/material-symbols-outlined.woff2')
