"""Builds the bundled Countdown word list from a SCOWL / ESDB export.

Download the source list (British spellings, size 70) from:
  http://app.aspell.net/create?max_size=70&spelling=GBs&spelling=GBz&max_variant=0&diacritic=strip&download=wordlist&encoding=utf-8&format=inline

Usage:
  python -I scripts/build_wordlist.py <scowl.txt>

Writes:
  composeApp/src/commonMain/composeResources/files/words.txt   (one word per line, a-z, 2-9 letters)
  third_party/SCOWL-LICENSE.txt                                (copyright notice required by SCOWL)
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT_WORDS = ROOT / "composeApp/src/commonMain/composeResources/files/words.txt"
OUT_LICENSE = ROOT / "third_party/SCOWL-LICENSE.txt"
WORD = re.compile(r"^[a-z]{2,9}$")  # lowercase only: drops proper nouns, abbreviations and possessives


def main(src: str) -> None:
    text = pathlib.Path(src).read_text(encoding="utf-8")
    header, sep, body = text.partition("\n---\n")
    if not sep:
        sys.exit("Unexpected format: no '---' separator between header and words")
    words = sorted({w.strip() for w in body.splitlines() if WORD.match(w.strip())})
    OUT_WORDS.parent.mkdir(parents=True, exist_ok=True)
    OUT_WORDS.write_text("\n".join(words) + "\n", encoding="utf-8", newline="\n")
    OUT_LICENSE.parent.mkdir(parents=True, exist_ok=True)
    OUT_LICENSE.write_text(header.strip() + "\n", encoding="utf-8", newline="\n")
    nine = sum(1 for w in words if len(w) == 9)
    print(f"{len(words)} words written ({nine} nine-letter words)")


if __name__ == "__main__":
    main(sys.argv[1])
