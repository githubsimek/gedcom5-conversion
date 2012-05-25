/**
 * Copyright 2012 Intellectual Reserve, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gedcomx.conversion.gedcom.dq55;

import org.folg.gedcom.model.Submitter;
import org.gedcomx.conversion.GedcomxConversionResult;
import org.gedcomx.metadata.foaf.Person;
import org.gedcomx.metadata.rdf.RDFLiteral;

import java.io.IOException;


public class SubmitterMapper {
  public void toContributor(Submitter dqSubmitter, GedcomxConversionResult result) throws IOException {
    Person gedxContributor = new Person();

    CommonMapper.populateAgent(gedxContributor, dqSubmitter.getId(), dqSubmitter.getName(), dqSubmitter.getAddress(), dqSubmitter.getPhone(), dqSubmitter.getFax(), dqSubmitter.getEmail(), dqSubmitter.getWww());

    if (dqSubmitter.getLanguage() != null) {
      gedxContributor.setLanguage(new RDFLiteral(dqSubmitter.getLanguage()));
    }

    // TODO: add logging for fields we are not processing right now
//    dqSubmitter.getId();
//    dqSubmitter.getName();
//    dqSubmitter.getRin();
//    dqSubmitter.getValue();
//    dqSubmitter.getExtensions();

    result.setDatasetContributor(gedxContributor, CommonMapper.toDate(dqSubmitter.getChange()));
  }
}