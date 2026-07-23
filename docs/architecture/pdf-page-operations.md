# Structure-preserving PDF page operations

Phase 8 adds a separate `StructurePreservingPdfEngine` behind the existing single-module processing
boundary. It does not replace the Android platform engine used for inspection, preview, images to
PDF, or explicitly acknowledged raster compatibility compression.

For supported inputs, PdfBox-Android imports requested page dictionaries and content streams into a
new page tree. Merge, page extraction/split, reorder, rotation, and deletion therefore retain text,
vector, image, link, and page-annotation objects instead of converting every page into a bitmap.
The edit plan is bounded to 10 source PDFs, 100 total output pages, and 200 MiB of staged input.

The compatibility boundary is deliberately strict:

- encrypted PDFs are rejected; FormReady does not request or persist passwords;
- AcroForms are rejected because page extraction can break document-level form relationships;
- digitally signed PDFs are rejected because any page-tree edit invalidates the signature;
- document-level outlines and metadata are not claimed to remain byte-for-byte unchanged.

PdfBox-Android 2.0.27.0 is the latest published Android port but is based on an older Apache
PDFBox line. The release audit must therefore recheck the upstream
[PDFBox security page](https://pdfbox.apache.org/security.html) and the
[Android port](https://github.com/TomRoush/PdfBox-Android) before every upgrade or release.
FormReady does not include PDFBox command-line examples or embedded-file extraction, and its
input/page/size bounds remain required because malformed PDFs can still consume substantial CPU or
memory.

Inputs use the existing bounded `noBackupFilesDir` staging path. The engine writes a private partial
candidate, removes it on failure/cancellation, and saves only to app-owned output storage. The
independent Android platform renderer then reopens the result, checks page count and dimensions
against the requested order/rotation, and renders every page before the result is reported Ready.
