
/* ====================================================================
   Copyright 2002-2004   Apache Software Foundation

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
==================================================================== */

package org.apache.poi.hwpf.usermodel;

public class TableExtractor {


static public Table  getTable (Range range, int startIndex, int levelNum)  {
//    System.out.println("-------SEARCH THE TABLE-------------");
    int _index = startIndex;

    int numParagraphs = range.numParagraphs();
    int endIndex = numParagraphs;

    for (;_index < numParagraphs; _index++)    {
      Paragraph paragraph = range.getParagraph(_index);

  //    System.out.println(paragraph.text());

      if (!paragraph.isInTable() || paragraph.getTableLevel() < levelNum){
        endIndex = _index;
        break;
      }
    }

    //System.out.println("startP: "+startIndex+" endP: "+endIndex+" "+levelNum);
    return new Table(startIndex, endIndex, range, levelNum);
  }

}
