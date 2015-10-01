#!/usr/bin/env perl
use strict;
use warnings;
use FileHandle;
use MIME::Base64;
use File::Slurp qw(slurp);

use JSON;
use CGI;
use Imager;
use DBI;
use DBD::ODBC;

my $pixelsize = 0.48;
my $hirespixelsize = 0.1253;

my $cgi = CGI->new;

my $slide = 1;

print $cgi->start_html;
print $cgi->start_body;

while (@ARGV) {
  my $dir1=shift(@ARGV);
  my $dir2=shift(@ARGV) or next;

  # get the position names and position into a grid of [col][row]
  my @posnames = sort grep {-d} glob("$dir1/*");
  my @pos;
  for my $posname (@posnames) {
    my %posname = (
      posname=>$posname,
      col=>[/^1-Pos_([0-9]+)_[0-9]+$/]->[0],
      row=>[/^1-Pos_[0-9]+_([0-9]+)$/]->[0],
      imagefile=>[glob("$dir1/$posname/*.tif")]->[0],
      metadata=>JSON::from_json(slurp([glob("$dir1/$posname/metadata.txt")]->[0])),
    );
    $pos[$posname{row}][$posname{col}] = \%posname;
  }

  my @roitiles = sort grep {-d} glob("$dir2/*");
  for my $roitile (@roitiles) {
    my %roitile = (
      posname=>$roitile=~[/^(1-Pos_[0-9]+_[0-9]+)/]->[0],
      col=>$roitile=~[/^1-Pos_([0-9]+)_[0-9]+/]->[0],
      row=>$roitile=~[/^1-Pos_[0-9]+_([0-9]+)/]->[0],
      imageLabel=>$roitile=~[/\(([0-9]+_[0-9]+_[0-9]+_[0-9]+)\)/]->[0],
      channel=>$roitile=~[/\(([0-9]+)_[0-9]+_[0-9]+_[0-9]+\)/]->[0],
      slice=>$roitile=~[/\([0-9]+_([0-9]+)_[0-9]+_[0-9]+\)/]->[0],
      frame=>$roitile=~[/\([0-9]+_[0-9]+_([0-9]+)_[0-9]+\)/]->[0],
      position=>$roitile=~[/\([0-9]+_[0-9]+_[0-9]+_([0-9]+)\)/]->[0],
      roi=>$roitile=~[/ROI=([0-9]+)/]->[0],
      tilex=>$roitile=~[/tileX=([0-9]+)/]->[0],
      tiley=>$roitile=~[/tileY=([0-9]+)/]->[0],
      imagefiles=>[sort glob("$dir2/$roitile/*.tif")],
      metadata=>JSON::from_json(slurp([glob("$dir2/$roitile/metadata.txt")]->[0])),
    );
    %{$pos[$roitile{row}][$roitile{col}]} = (
      %{$pos[$roitile{row}][$roitile{col}]||{}},
      imageLabel=>$roitile{imageLabel},
      channel=>$roitile{channel},
      slice=>$roitile{slice},
      frame=>$roitile{frame},
      position=>$roitile{poisition},
    );
    $pos[$roitile{row}][$roitile{col}]{rois}{$roitile{roi}}{tiles}[$roitile{tilex}][$roitile{tiley}] = \%roitile;
  }

  print $cgi->h1("Slide $slide");

  print $cgi->start_table;
  print $cgi->start_tbody;
  for my $row (@pos) {
    print $cgi->start_Tr;
    for my $col (@{$pos[$row]}) {
      my $pos = $pos[$row][$col];
      print $cgi->start_td;
      print $cgi->dl(map {($cgi->dt($_), $cgi->dd($pos->{$_}))} sort keys %$pos);
      my $img = Imager->new($pos->{imagefile}) or die Imager->errstr;
      for my $roi (@{$pos->{rois}||[]}) {
        # convert each tile stage position into x,y,w,h box coordinates of the
        # low-res image
        
      }
      print $cgi->end_td;
    }
    print $cgi->end_Tr;
  }
  print $cgi->end_tbody;
  print $cgi->end_table;

  ++$slide;
}

print $cgi->end_body;
print $cgi->end_html;

exit 0;
