# android-pathbitmap

Simple example of how you can tackle the `Shape path too large to be rendered into a texture` warning on the GPU
accelerated views while drawing the large Path.

## Background

Paths are always drawn using the CPU, but when enclosing View is GPU accelerated then the path gets drawn
onto the texture first and then this texture is drawn onto the screen.

There is a problem with such approach though. If Path is large enough, it can exceed the maximum allowed texture size.
In that case the Path simply is not drawn at all -- OpenGL disallows such operation due to hardware limitations.

## Solution

We may though overcome that by drawing such a large Path into the own managed Bitmap, which dimensions are the same
as the ones coming from the View. By translating the Bitmap's Canvas we can control which part of the Path is drawn
onto the Bitmap. It works well, because, as of Android 4.2, Canvas associated with the Bitmap is not GPU accelerated.

As soon as drawing on bitmap is done, this Bitmap may be simply drawn onto the View's canvas. Even though
it is GPU accelerated, the Bitmap in its maximum is of the screen size and it is guaranteed that it fits
onto the maximum texture size.