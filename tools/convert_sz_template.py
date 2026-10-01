#!/usr/bin/env python3
"""
Разовый конвертер образца «Договор СЗ образец.docx» в шаблон с плейсхолдерами.

Замены точечные: конкретные значения заменяются на плейсхолдеры прямо на своих
местах, пробелы/табуляции/выравнивание не трогаются — разметка страниц
совпадает с оригиналом. Дополнительно снимается цветовая подсветка
(в образце она была только для объяснения ТЗ).

Запуск (из корня проекта):
    python tools/convert_sz_template.py <исходный.docx> templates/ДоговорСЗ.docx
"""
import re
import sys

import docx

# (регулярное выражение, замена, ожидаемое число замен)
# Значения в правилах — вымышленные, от любых реальных людей и организаций
# они отличаются; конвертер предназначен для образца «Договор СЗ образец.docx».
RULES = [
    # номер договора (договор + акт)
    (r"42-СЗ", r"{{contract.number}}-СЗ", 2),
    # даты
    (r"«\s*22\s*»\s*октября\s*2025\s*г\.", r"{{event.дата_договора}}", 2),
    (r"«\s*23\s*»\s*октября\s*2025\s*г\.", r"{{event.дата_акта}}", 1),
    # вводная часть и акт: ФИО + дата рождения (4 раза)
    (r"Иванов Иван Иванович", r"{{person.ФИО}}", 4),
    (r"01\.01\.1990", r"{{person.Дата рождения}}", 4),
    (r"ИНН –\s*123456789012", r"ИНН – {{person.ИНН}}", 2),
    # предмет договора
    (r"услуги\s+судьи\s*2\s*категории", r"{{person.Вид услуг}}", 2),
    (r"Чемпионата города по спортивному ориентированию",
     r"{{event.название_мероприятия}}", 2),
    (r"с\s*22\s*по\s*23\s*октября\s*2025\s*года", r"{{event.срок_услуг}}", 2),
    # реквизиты банка — раньше правила суммы, т.к. номер счёта содержит «10800»
    (r"Банк: ПАО «БАНК», БИК 000000000, ИНН/КПП 0000000000/000000000, К/С 00000000000000000000, Л/С 00000000000000000000\.",
     r"Банк: {{person.Банк}}, БИК {{person.БИК}}, К/С {{person.К/С}}, Л/С {{person.Л/С}}.", 2),
    # сумма — третий вид параметров: вводится при генерации для каждого человека
    (r"(?<!\d)10\s*800(?!\d)", r"{{each.Сумма}}", 2),
    (r"\(Десять тысяч восемьсот\)", r"({{each.Сумма:прописью}})", 2),
    (r"01\.11\.2025", r"{{event.срок_оплаты}}", 1),
    # реквизиты (договор + акт) — точечно, отступы сохраняются
    (r"серия 00 00 № 000000", r"серия {{person.Паспорт серия}} № {{person.Паспорт номер}}", 2),
    (r"выдан 01\.01\.2020 г\. ОТДЕЛОМ УФМС РОССИИ ПО Г\. МОСКВЕ\.",
     r"выдан {{person.Паспорт дата выдачи}} г. {{person.Паспорт кем выдан}}.", 2),
    (r"Код подразделения 000-000\.", r"Код подразделения {{person.Код подразделения}}.", 2),
    (r"г\. Москва, ул\. Примерная, д\. 1, кв\. 1\.",
     r"{{person.Адрес регистрации}}", 2),
    (r"ИНН 123456789012", r"ИНН {{person.ИНН}}", 2),
    (r"СНИЛС 000-000-000 00", r"СНИЛС {{person.СНИЛС}}", 2),
    # подпись
    (r"И\.И\. Иванов", r"{{person.ФИО:инициалы}}", 2),
]


def iter_paragraphs(document):
    for p in document.paragraphs:
        yield p
    for table in document.tables:
        yield from iter_table(table)
    for section in document.sections:
        for part in (section.header, section.footer):
            if part.is_linked_to_previous:
                continue
            for p in part.paragraphs:
                yield p
            for table in part.tables:
                yield from iter_table(table)


def iter_table(table):
    for row in table.rows:
        for cell in row.cells:
            for p in cell.paragraphs:
                yield p
            for t in cell.tables:
                yield from iter_table(t)


def replace_range(runs, texts, start, end, replacement):
    """Заменить combined[start:end] на replacement, распределив по runs.

    run.text в python-docx переводит \\t и \\n обратно в <w:tab/> и <w:br/>,
    поэтому выравнивание и разрывы строк/страниц сохраняются.
    """
    pos = 0
    first = True
    for i, run in enumerate(runs):
        run_start, run_end = pos, pos + len(texts[i])
        pos = run_end
        ov_start, ov_end = max(start, run_start), min(end, run_end)
        if ov_start >= ov_end:
            continue
        local_start, local_end = ov_start - run_start, ov_end - run_start
        insert = replacement if first else ""
        first = False
        new_text = texts[i][:local_start] + insert + texts[i][local_end:]
        run.text = new_text
        texts[i] = new_text


def clear_highlight(document):
    for p in iter_paragraphs(document):
        for run in p.runs:
            if run.font.highlight_color is not None:
                run.font.highlight_color = None


def postprocess_docx(path):
    """Финальная зачистка zip'а на уровне XML:

    - снять подсветку с меток абзацев (w:pPr/w:rPr) — python-docx их не покрывает;
    - удалить пустые header/footer, которые python-docx создаёт при обходе секций:
      они добавляют отступ под колонтитул и сдвигают текст, ломая разметку страниц.
    """
    import shutil
    import zipfile

    tmp = path + ".tmp"
    with zipfile.ZipFile(path) as zin:
        names = zin.namelist()
        header_footer = [n for n in names if re.fullmatch(r"word/(header|footer)\d+\.xml", n)]
        with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as zout:
            for item in zin.infolist():
                if item.filename in header_footer:
                    continue
                data = zin.read(item.filename)
                if item.filename == "word/document.xml":
                    text = data.decode("utf-8")
                    text = re.sub(r'<w:highlight w:val="[^"]*"\s*/>', "", text)
                    text = re.sub(r'<w:(header|footer)Reference[^/]*/>', "", text)
                    data = text.encode("utf-8")
                elif item.filename == "word/_rels/document.xml.rels":
                    text = data.decode("utf-8")
                    for n in header_footer:
                        text = re.sub(
                            r'<Relationship [^>]*Target="' + re.escape(n.replace("word/", "")) + r'"[^>]*/>',
                            "", text)
                    data = text.encode("utf-8")
                elif item.filename == "[Content_Types].xml":
                    text = data.decode("utf-8")
                    for n in header_footer:
                        text = re.sub(
                            r'<Override [^>]*PartName="/' + re.escape(n) + r'"[^>]*/>', "", text)
                    data = text.encode("utf-8")
                zout.writestr(item, data)
    shutil.move(tmp, path)


def main():
    src, dst = sys.argv[1], sys.argv[2]
    document = docx.Document(src)
    counts = [0] * len(RULES)

    for paragraph in iter_paragraphs(document):
        runs = paragraph.runs
        if not runs:
            continue
        texts = [r.text or "" for r in runs]
        combined = "".join(texts)
        for ri, (pattern, replacement, _expected) in enumerate(RULES):
            matches = list(re.finditer(pattern, combined))
            for m in reversed(matches):
                replace_range(runs, texts, m.start(), m.end(),
                              re.sub(pattern, replacement, m.group(0)))
                counts[ri] += 1
            if matches:
                combined = "".join(texts)

    clear_highlight(document)
    document.save(dst)
    postprocess_docx(dst)

    errors = []
    for (pattern, _replacement, expected), actual in zip(RULES, counts):
        if actual != expected:
            errors.append(f"  {pattern!r}: ожидалось {expected}, заменено {actual}")

    if errors:
        print("ОШИБКИ в количестве замен:")
        print("\n".join(errors))
        sys.exit(1)
    print(f"OK: все {len(RULES)} правил применены ожидаемое число раз -> {dst}")


if __name__ == "__main__":
    main()
