# -*- coding: utf-8 -*-

import re
import pytest


youtube_regex = re.compile(
    r'(https?://)?(www\.)?'
    '(youtube|youtu|youtube-nocookie)\.([A-Za-z]*)/'
    '(watch\?v=|embed/|v/|.+\?v=)?([^&=%\?]{11})')


#
# PARSING
# See: http://stackoverflow.com/questions/4705996/python-regex-convert-youtube-url-to-youtube-video
#


@pytest.mark.parametrize("expected,example", [
    ("C2vgICfQawE", "http://www.youtube.com/watch?v=C2vgICfQawE"),
    ("C2vgICfQawE",
    """'<object width="480" height="385">
            <param name="movie" value="http://www.youtube.com/v/C2vgICfQawE?fs=1"></param>
            <param name="allowFullScreen" value="true"></param>
            <param name="allowscriptaccess" value="always"></param><embed src="http://www.youtube.com/v/C2vgICfQawE?fs=1"
                type="application/x-shockwave-flash" allowscriptaccess="always" allowfullscreen="true" width="480" height="385"></embed>
    </object>'"""),
    ('5Y6HSHwhVlY', 'http://www.youtube.com/watch?v=5Y6HSHwhVlY'),
    ('5Y6HSHwhVlY', 'http://youtu.be/5Y6HSHwhVlY'),
    ('5Y6HSHwhVlY', 'http://www.youtube.com/embed/5Y6HSHwhVlY?rel=0" frameborder="0"'),
    ('5Y6HSHwhVlY', 'https://www.youtube-nocookie.com/v/5Y6HSHwhVlY?version=3&amp;hl=en_US'),
    ('', 'http://www.youtube.com/'),
    ('', 'http://www.youtube.com/?feature=ytca'),
])
def test_parsing(expected, example):
    video_id = parse_video_id(example)
    assert expected == video_id, "'%s' <> '%s' for %s" % (expected, video_id, example)


def parse_video_id(text):
    matches = youtube_regex.findall(text)
    if matches:
        return matches[0][5]  # Pick the group(6) from the first match
    return ""



#
# THUMBNAILS
# See: http://stackoverflow.com/questions/2068344/how-to-get-thumbnail-of-youtube-video-link-using-youtube-api?rq=1
#

tb_0 = "http%s://img.youtube.com/vi/%s/0.jpg"
tb_1 = "http%s://img.youtube.com/vi/%s/1.jpg"
tb_2 = "http%s://img.youtube.com/vi/%s/2.jpg"
tb_3 = "http%s://img.youtube.com/vi/%s/3.jpg"
tb_def = "http%s://img.youtube.com/vi/%s/default.jpg"
tb_hi = "http%s://img.youtube.com/vi/%s/hqdefault.jpg"
tb_med = "http%s://img.youtube.com/vi/%s/mqdefault.jpg"
tb_std = "http%s://img.youtube.com/vi/%s/sddefault.jpg"
tb_maxres = "http%s://img.youtube.com/vi/%s/maxresdefault.jpg"
json = "http%s://gdata.youtube.com/feeds/api/videos/%s?v=2&prettyprint=true&alt=json"

def get_thumbnail(video_id, which=0, https=False):
    fmt = globals()["tb_%s" % which]
    return fmt % ( https and "s" or "", video_id)

@pytest.mark.parametrize("expected,https,which", [
    ("https://img.youtube.com/vi/FOO/0.jpg", True, 0),
    ("http://img.youtube.com/vi/FOO/0.jpg", False, 0),
    ("http://img.youtube.com/vi/FOO/hqdefault.jpg", False, "hi"),
])
def test_thumbnails(expected, https, which):
    url = get_thumbnail("FOO", which=which, https=https)
    assert expected == url

#
# IFRAME
# See: https://developers.google.com/youtube/youtube_player_demo
#

youtube_iframe = (
    '<iframe id="ytplayer" type="text/html" width="120" height="67.5" '
    'src="https://www.youtube.com/embed/%(video_id)s?autoplay=%(autoplay)s&loop=%(loop)s&color=white&theme=light"'
    'frameborder="0" allowfullscreen>'
    )

def get_iframe(video_id, autoplay=False, loop=False):
    return youtube_iframe % {
        "video_id": video_id,
        "autoplay": autoplay and 1 or 0,
        "loop": loop and 1 or 0,
    }

@pytest.mark.parametrize("contains,autoplay,loop", [
    ("autoplay=0&loop=0", 0, 0),
    ("autoplay=0&loop=0", 0, False),
    ("autoplay=0&loop=0", False, False),
    ("autoplay=1&loop=0", 1, 0),
    ("autoplay=1&loop=0", 1, False),
    ("autoplay=1&loop=0", True, False),
    ("autoplay=0&loop=1", 0, 1),
])
def test_thumbnails(contains, autoplay, loop):
    iframe = get_iframe("FOO", autoplay, loop)
    assert contains in iframe
