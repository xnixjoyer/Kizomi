          package com.anisync.android.presentation.library

          import org.junit.Assert.assertEquals
          import org.junit.Test

          class LibraryChromeTest {
              @Test
              fun `search collapse offset is bounded and shared by selector and tabs`() {
                  assertEquals(0, coordinatedLibraryChromeOffset(25f, 64))
                  assertEquals(-31, coordinatedLibraryChromeOffset(-31.4f, 64))
                  assertEquals(-64, coordinatedLibraryChromeOffset(-200f, 64))
              }

              @Test
              fun `coordinated chrome releases exactly the collapsed search height`() {
                  assertEquals(220, coordinatedLibraryChromeHeight(220, 0))
                  assertEquals(189, coordinatedLibraryChromeHeight(220, -31))
                  assertEquals(156, coordinatedLibraryChromeHeight(220, -64))
                  assertEquals(0, coordinatedLibraryChromeHeight(20, -64))
              }
          }
