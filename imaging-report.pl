#!/usr/bin/env perl
use strict;
use warnings;
use FileHandle;
use MIME::Base64;
use File::Slurp qw(slurp);
use POSIX qw(ceil);

use JSON;
use CGI;
use Imager;
use DBI;
use DBD::ODBC;

my $dsn = [slurp([glob('*.lock')]->[0], chomp=>1)]->[0] or die "Could not read DSN";
my ($user, $pass) = slurp([glob('*.login')]->[0], chomp=>1) or die "Could not read login info";
my $dbh = DBI->connect("dbi:ODBC:DSN=$dsn", $user, $pass)
  or die "Could not open DB handle $dsn: ".DBI->errstr;
$dbh->{RaiseError} = 1;

my $cgi = CGI->new;
print $cgi->start_html;
print $cgi->start_body;

my $slide = 1;
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
      metadata=>JSON::from_json([slurp([glob("$dir1/$posname/metadata.txt")]->[0], chomp=>1)]->[0]),
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
      metadata=>JSON::from_json([slurp([glob("$dir2/$roitile/metadata.txt")]->[0], chomp=>1)]->[0]),
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
      for my $roi_id (sort keys %{$pos->{rois}||[]}) {
        my $roi = $pos->{rois}{$roi_id};
        # convert each tile stage position into x,y,w,h box coordinates of the
        # low-res image

        my $pixelSizeUm = 0.48;
        my $hiResPixelSizeUm = 0.1253;
        my $cameraWidth = 4928;
        my $cameraHeight = 3264;
        my $overlapPct = 25;

        my $r = query($dbh, "select * from roi where \"id\"=?", $roi_id);
        my ($roiX1, $roiX2, $roiY1, $roiY2) = ($r->{x1}, $r->{x2}, $r->{y1}, $r->{y2});

        my $roiWidth = $roiX2-$roiX1+1;
        my $roiHeight = $roiY2-$roiY1+1;
        my $tileWidth = int(($cameraWidth * $hiResPixelSizeUm) / $pixelSizeUm);
        my $tileHeight = int(($cameraHeight * $hiResPixelSizeUm) / $pixelSizeUm);
        my $tileXOverlap = int(($overlapPct / 100.0) * $tileWidth);
        my $tileYOverlap = int(($overlapPct / 100.0) * $tileHeight);
        my $tileXCount = ceil(($roiWidth - $tileXOverlap) / ($tileWidth - $tileXOverlap));
        my $tileYCount = ceil(($roiHeight - $tileYOverlap) / ($tileHeight - $tileYOverlap));
        my $tileSetWidth = ($tileXCount * ($tileWidth - $tileXOverlap)) + $tileXOverlap;
        my $tileSetHeight = ($tileYCount * ($tileHeight - $tileYOverlap)) + $tileYOverlap;
        my $tileXOffset = int(($roiX1 + ($roiWidth / 2.0)) - ($tileSetWidth / 2.0) + ($tileWidth / 2.0));
        my $tileYOffset = int(($roiY1 + ($roiHeight / 2.0)) - ($tileSetHeight / 2.0) + ($tileHeight / 2.0));

        for (my $x=0; $x < $tileXCount; ++$x) {
          for (my $y=0; $y < $tileYCount; ++$y) {
            my $tileX = ($x*($tileWidth - $tileXOverlap)) + $tileXOffset;
            my $tileY = ($y*($tileHeight - $tileYOverlap)) + $tileYOffset;

            $img->box(
              color=>Imager::Color->new(0, 0, 0), 
              x1=>$tileX, y1=>$tileY, x2=>$tileX+$tileWidth, y2=>$tileY+$tileHeight, aa=>1, endp=>1);

          }
        }
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

sub query {
  my $dbh = shift;
  my $sql = shift;
  my $sth = $dbh->prepare($sql)
    or die "Could not prepare statement: $sql";
  $sth->execute(@_);
  my @ret;
  while (my $r = $sth->fetchrow_hashref) {
    push @ret, $r;
  }
  return @ret;
}
