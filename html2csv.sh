#!/usr/bin/env bash
exec perl -MHTML::TableExtract -MText::CSV -0777 -ne '$t=HTML::TableExtract->new->parse($_); $c=Text::CSV->new({binary=>1}); for $s ($t->tables) {for $r ($s->rows) {$c->combine(@$r); print $c->string."\n"}}' "$@"
