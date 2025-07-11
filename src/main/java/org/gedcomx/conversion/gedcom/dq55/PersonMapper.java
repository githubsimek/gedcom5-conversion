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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.GedcomTag;
import org.folg.gedcom.model.LdsOrdinance;
import org.gedcomx.common.URI;
import org.gedcomx.conclusion.Fact;
import org.gedcomx.conclusion.Gender;
import org.gedcomx.conclusion.Identifier;
import org.gedcomx.conclusion.Name;
import org.gedcomx.conclusion.NameForm;
import org.gedcomx.conclusion.NamePart;
import org.gedcomx.conclusion.Person;
import org.gedcomx.conversion.GedcomxConversionResult;
import org.gedcomx.source.SourceReference;
import org.gedcomx.types.GenderType;
import org.gedcomx.types.NamePartType;
import org.gedcomx.types.NameType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

import org.familysearch.platform.ordinances.Ordinance;


public class PersonMapper {
  private static final Logger logger = LoggerFactory.getLogger(CommonMapper.class);

  private final MappingConfig mappingConfig;
  private final PostProcessor postProcessor;

  public PersonMapper(MappingConfig mappingConfig) {
    this(mappingConfig, null);
  }

  public PersonMapper(MappingConfig mappingConfig, PostProcessor postProcessor) {
    this.mappingConfig = mappingConfig;
    this.postProcessor = postProcessor;
  }

  public void toPerson(org.folg.gedcom.model.Person dqPerson, GedcomxConversionResult result) throws IOException {
    if (dqPerson == null) {
      return;
    }

    Marker personContext = ConversionContext.getDetachedMarker(String.format("@%s@ INDI", dqPerson.getId()));
    ConversionContext.addReference(personContext);
    try {
      Person gedxPerson = new Person();
      gedxPerson.setId(mappingConfig.createId(dqPerson.getId()));

      //////////////////////////////////////////////////////////////////////
      // Process NAMES

      int index = 0;
      List<Name> gedxNames = new ArrayList<Name>();
      for (org.folg.gedcom.model.Name dqName : dqPerson.getNames()) {
        Marker nameContext = ConversionContext.getDetachedMarker("NAME." + (++index));
        ConversionContext.addReference(nameContext);
        try {
          int cntNamesBeforeThisNameObj = gedxNames.size();
          gedxNames.addAll(toNameList(dqName, result));
          if ((cntNamesBeforeThisNameObj == 0) && (gedxNames.size() > 0)) {
            // the first name encountered is assumed to be the preferred name per the recommendation given in the GEDCOM 5.5.1 specification
            gedxNames.get(0).setPreferred(Boolean.TRUE);
          }
        } finally {
          ConversionContext.removeReference(nameContext);
        }
      }

      if (gedxNames.size() > 0) {
        gedxPerson.setNames(gedxNames);
      }


      //////////////////////////////////////////////////////////////////////
      // Process facts

      processFacts(gedxPerson, dqPerson.getEventsFacts(), result);

      //////////////////////////////////////////////////////////////////////
      // Process ordinances

      processOrdinances(gedxPerson, dqPerson.getLdsOrdinances());

      //////////////////////////////////////////////////////////////////////
      // Process sources

      List<SourceReference> sources = CommonMapper.toSourcesAndSourceReferences(dqPerson.getSourceCitations(), result);
      gedxPerson.setSources(sources);


      //////////////////////////////////////////////////////////////////////
      // Add the person to the conversion results
      java.util.Date lastModified = CommonMapper.toDate(dqPerson.getChange()); //todo: set the timestamp on the attribution?


      //////////////////////////////////////////////////////////////////////
      // Warn about all fields we are not processing

      if (dqPerson.getAssociations() != null && dqPerson.getAssociations().size() > 0) {
        logger.warn(ConversionContext.getContext(), "Associations ignored.");
      }
      if (dqPerson.getRecordFileNumber() != null) {
        logger.warn(ConversionContext.getContext(), "Record file number ignored: {}", dqPerson.getRecordFileNumber());
      }
      if (dqPerson.getReferenceNumbers() != null && dqPerson.getReferenceNumbers().size() > 0) {
        for (String each : dqPerson.getReferenceNumbers()) {
          gedxPerson.addIdentifier(new Identifier().value(new URI(each)).type(new URI("USER_REFERENCE_NUMBER")));
        }
      }

      if (dqPerson.getAncestorInterestSubmitterRef() != null) {
        logger.warn(ConversionContext.getContext(), "Ancestor interest ignored: {}.", dqPerson.getAncestorInterestSubmitterRef());
      }

      if (dqPerson.getDescendantInterestSubmitterRef() != null) {
        logger.warn(ConversionContext.getContext(), "Descendant interest ignored: {}.", dqPerson.getDescendantInterestSubmitterRef());
      }

      if (dqPerson.getAddress() != null) {
        logger.warn(ConversionContext.getContext(), "Address was ignored: {}", dqPerson.getAddress().getDisplayValue());
      }

      if (dqPerson.getEmail() != null) {
        logger.warn(ConversionContext.getContext(), "e-mail ({}) was ignored.", dqPerson.getEmail());
      }
      if (dqPerson.getFax() != null) {
        logger.warn(ConversionContext.getContext(), "fax ({}) was ignored.", dqPerson.getFax());
      }
      if (dqPerson.getPhone() != null) {
        logger.warn(ConversionContext.getContext(), "phone ({}) was ignored.", dqPerson.getPhone());
      }
      if (dqPerson.getWww() != null) {
        logger.warn(ConversionContext.getContext(), "www ({}) was ignored.", dqPerson.getWww());
      }

      if (dqPerson.getUid() != null) {
        Marker uidContext = ConversionContext.getDetachedMarker(dqPerson.getUidTag());
        ConversionContext.addReference(uidContext);
        logger.warn(ConversionContext.getContext(), "UID ({}) was ignored.", dqPerson.getUid());
        ConversionContext.removeReference(uidContext);
      }

      if (dqPerson.getRin() != null) {
        logger.warn(ConversionContext.getContext(), "RIN ({}) was ignored.", dqPerson.getRin());
      }

      int cntNotes = dqPerson.getNotes().size() + dqPerson.getNoteRefs().size();
      if (cntNotes > 0) {
        logger.warn(ConversionContext.getContext(), "Did not process {} notes or references to notes.", cntNotes);
      }

      int cntMedia = dqPerson.getMedia().size() + dqPerson.getMediaRefs().size();
      if (cntMedia > 0) {
        logger.warn(ConversionContext.getContext(), "Did not process {} media items or references to media items.", cntMedia);
      }

      if (dqPerson.getExtensions().size() > 0) {
        for (String extensionCategory : dqPerson.getExtensions().keySet()) {
          for (GedcomTag tag : ((List<GedcomTag>)dqPerson.getExtension(extensionCategory))) {
            logger.warn(ConversionContext.getContext(), "Unsupported ({}): {}", extensionCategory, tag);
            // DATA tag (and subordinates) in GEDCOM 5.5. SOURCE_RECORD not being looked for or parsed by DallanQ code
          }
        }
      }

      if (postProcessor != null) {
        postProcessor.postProcessPerson(dqPerson, gedxPerson);
      }

      result.addPerson(gedxPerson);
    } finally {
      ConversionContext.removeReference(personContext);
    }
  }

  private void processFacts(Person gedxPerson, List<EventFact> facts, GedcomxConversionResult result) throws IOException {
    if(facts == null) {
      return;
    }

    int index = 0;
    for(EventFact fact : facts) {
      Marker factContext = ConversionContext.getDetachedMarker(fact.getTag() + '.' + (++index));
      ConversionContext.addReference(factContext);
      try {
        Fact gedxFact = FactMapper.toFact(fact, result);

        if(gedxFact == null) {
          fact.getType();
          if(fact.getTag() != null && fact.getTag().equalsIgnoreCase("SEX")) {
            processSex(gedxPerson, fact);
          }
        }

        if(gedxFact != null) {
          gedxPerson.addFact(gedxFact);
        }
      } finally {
        ConversionContext.removeReference(factContext);
      }
    }
  }

  private void processOrdinances(Person gedxPerson, List<LdsOrdinance> ordinances) throws IOException {
    if(ordinances == null) {
      return;
    }

    int index = 0;
    for(LdsOrdinance ord : ordinances) {
      Marker ordinanceContext = ConversionContext.getDetachedMarker(ord.getTag() + '.' + (++index));
      ConversionContext.addReference(ordinanceContext);
      try {
        Fact ordFact = FactMapper.toOrdinance(ord);

        Ordinance ordinance = new Ordinance();
        ordinance.setCompleteDate(ordFact.getDate());
        ordinance.setType(ordFact.getType());

        if (ordinance.getCompleteDate().getOriginal().length() < 5 || ordinance.getCompleteDate().getOriginal().isEmpty()) {
          System.out.println("Error: Missing date for " + gedxPerson.getId().toString() +
                " ordinance: " + ordFact.getType().toString());
        }

        if (ordFact.getQualifiers() == null) {
          System.out.println("Error: Missing qualifier (status or temple code) for  " + gedxPerson.getId().toString() +
                  " ordinance: " + ordFact.getType().toString());
        }
        else if (ordFact.getQualifiers().size() == 1) {
          ordinance.setStatus(ordFact.getQualifiers().get(0).getName());
        }
        else if (ordFact.getQualifiers().size() == 2) {
          ordinance.setTempleCode(ordFact.getQualifiers().get(0).getValue());
          ordinance.setStatus(ordFact.getQualifiers().get(1).getName());
        }
        gedxPerson.addExtensionElement(ordinance);
//        gedxPerson.addFact(FactMapper.toOrdinance(ordinance));
      } finally {
        ConversionContext.removeReference(ordinanceContext);
      }
    }
  }

  private void processSex(Person gedxPerson, EventFact fact) {
    if(gedxPerson.getGender() != null) {
      logger.warn(ConversionContext.getContext(), "Missing gender designation");
    }

    if(fact.getValue().equalsIgnoreCase("M")) {
      gedxPerson.setGender(new Gender(GenderType.Male));
    }
    else if(fact.getValue().equalsIgnoreCase("F")) {
      gedxPerson.setGender(new Gender(GenderType.Female));
    }
    else if(fact.getValue().equalsIgnoreCase("U")) {
      gedxPerson.setGender(new Gender(GenderType.Unknown));
    }
    else  {
      logger.warn(ConversionContext.getContext(), "Unrecognized gender designation ({})", fact.getValue());
    }
  }

  private List<Name> toNameList(org.folg.gedcom.model.Name dqName, GedcomxConversionResult result) throws IOException {
    List<Name> nameList = new ArrayList<Name>();
    Boolean femaleWithSuffix = false;

    if (dqName == null) {
      return nameList;
    }

    Name gedxName = new Name();
    //gedxName.setId(); // no equivalent; probably system dependent anyway

    gedxName.setNameForms(new ArrayList<NameForm>());
    NameForm primaryForm = new NameForm();
    primaryForm.setFullText(getNameValue(dqName));
    if (primaryForm.getLang() == null) {
      primaryForm.setLang("ja");
    }
    List<NamePart> parts = getNameParts(dqName);
    // Check if there is a female Mrs suffix
    if (parts != null) {
      primaryForm.setParts(parts);
      for (NamePart part : parts) {
        if (part.getKnownType().equals(NamePartType.Suffix)
          && part.getValue().equals("夫人")) femaleWithSuffix = true;
      }

    }
    gedxName.getNameForms().add(primaryForm);

    if (dqName.getFone() != null) {
      List<NamePart> foneParts = new ArrayList<>();
      NameForm foneNameForm = new NameForm();
      foneNameForm.setLang("ja-Hrkt");
      foneNameForm.setFullText(dqName.getFone());

      // Add suffix part if suffix "夫人" exists for primaryForm
      if (femaleWithSuffix) {
        foneParts = newNamePartInstances("フジン", NamePartType.Suffix);
      }
      NamePart givenName = new NamePart();
      givenName.setKnownType(NamePartType.Given);

      // This checks if there is only a given name. Example: 1 NAME 伝右エ門 //
      if (dqName.getFone().contains("//")) {
        givenName.setValue(dqName.getFone().replace("//", "").trim());
        foneParts.add(givenName);
      }
      // This checks if there is a last name also. Example: 2 ROMN /Yoshimura/ Takichi
      else if (dqName.getFone().contains("/")) {
        if (dqName.getFone().split("/").length == 3) {
          givenName.setValue(dqName.getFone().split("/")[2]);
          NamePart lastName = new NamePart();
          lastName.setKnownType(NamePartType.Surname);
          lastName.setValue(dqName.getFone().split("/")[1]);
          foneParts.add(lastName);
        }
        else {
          givenName.setValue(dqName.getFone().split("/")[1]);
        }
        foneParts.add(givenName);
        foneNameForm.setFullText(dqName.getFone().replace("/", ""));
      }
      foneNameForm.setParts(foneParts);

      gedxName.getNameForms().add(foneNameForm);
    }

    if (dqName.getRomn() != null) {
      List<NamePart> romanParts = new ArrayList<>();
      NameForm romanNameForm = new NameForm();
      romanNameForm.setLang("ja-Latn");
      romanNameForm.setFullText(dqName.getRomn());

      // Add suffix part if suffix "夫人" exists for primaryForm
      if (femaleWithSuffix) {
        romanParts = newNamePartInstances("Fujin", NamePartType.Suffix);
      }
      NamePart givenName = new NamePart();
      givenName.setKnownType(NamePartType.Given);

      // This checks if there is only a given name. Example: 1 NAME 伝右エ門 //
      if (dqName.getRomn().contains("//")) {
        givenName.setValue(dqName.getRomn().replace("//", "").trim());
        romanParts.add(givenName);
      }
      // This checks if there is a last name also. Example: 2 ROMN /Yoshimura/ Takichi
      else if (dqName.getRomn().contains("/")) {
        if (dqName.getRomn().split("/").length == 3) {
          givenName.setValue(dqName.getRomn().split("/")[2]);
          NamePart lastName = new NamePart();
          lastName.setKnownType(NamePartType.Surname);
          lastName.setValue(dqName.getRomn().split("/")[1]);
          romanParts.add(lastName);
        } else {
          givenName.setValue(dqName.getRomn().split("/")[1]);
        }

        romanParts.add(givenName);
        romanNameForm.setFullText(dqName.getRomn().replace("/", ""));
      }
      romanNameForm.setParts(romanParts);

      gedxName.getNameForms().add(romanNameForm);
    }

    nameList.add(gedxName);

    if (dqName.getNickname() != null) {
      Name gedxNickname = new Name();
      gedxNickname.setKnownType(NameType.Nickname);
      NameForm nickname = new NameForm();
      nickname.setFullText(dqName.getNickname());
      gedxNickname.setNameForms(Arrays.asList(nickname));
      nameList.add(gedxNickname);
    }

    if (dqName.getMarriedName() != null) {
      Name gedxMarriedName = new Name();
      gedxMarriedName.setKnownType(NameType.MarriedName);
      NameForm marriedName = new NameForm();
      marriedName.setFullText(dqName.getMarriedName());
      gedxMarriedName.setNameForms(Arrays.asList(marriedName));
      nameList.add(gedxMarriedName);
    }

    if (dqName.getAka() != null) {
      Name gedxAka = new Name();
      gedxAka.setKnownType(NameType.AlsoKnownAs);
      NameForm alias = new NameForm();
      alias.setFullText(dqName.getMarriedName());
      gedxAka.setNameForms(Arrays.asList(alias));
      nameList.add(gedxAka);
    }

    if ((dqName.getSourceCitations() != null) && (dqName.getSourceCitations().size() > 0)) {
      List<SourceReference> sources = CommonMapper.toSourcesAndSourceReferences(dqName.getSourceCitations(), result);
      gedxName.setSources(sources);
    }

    if ((dqName.getType() != null) && (dqName.getType().trim().length() > 0)) {
      Marker nameTypeContext = ConversionContext.getDetachedMarker((dqName.getTypeTag() == null)?"Undetermined":dqName.getTypeTag());
      ConversionContext.addReference(nameTypeContext);
      logger.warn(ConversionContext.getContext(), "Name type ({}) was ignored.", dqName.getType());
      //gedxName.setKnownType();
      //gedxName.setType();
      ConversionContext.removeReference(nameTypeContext);
    }

    int cntNotes = dqName.getNotes().size() + dqName.getNoteRefs().size();
    if (cntNotes > 0) {
      logger.warn(ConversionContext.getContext(), "Did not process {} notes or references to notes.", cntNotes);
    }

    int cntMedia = dqName.getMedia().size() + dqName.getMediaRefs().size();
    if (cntMedia > 0) {
      logger.warn(ConversionContext.getContext(), "Did not process {} media items or references to media items.", cntMedia);
    }


    if (dqName.getExtensions().size() > 0) {
      for (String extensionCategory : dqName.getExtensions().keySet()) {
        for (GedcomTag tag : ((List<GedcomTag>)dqName.getExtension(extensionCategory))) {
          logger.warn(ConversionContext.getContext(), "Unsupported ({}): {}", extensionCategory, tag);
        }
      }
    }

    //dqName.getAkaTag() // data about GEDCOM 5.5 formatting that we will not preserve
    //dqName.getTypeTag() // data about GEDCOM 5.5 formatting that we will not preserve

    //dqName.getAllMedia(); // media not handled via this method; see getMedia and getMediaRefs
    //dqName.getAllNotes(); // notes not handled via this method; see getNotes and getNoteRefs

    //gedxName.setAttribution(); // DallanQ parser currently chooses not to handle per-item SUBM references
    //gedxName.setPreferred(); // handled outside this mapping method

    return nameList;
  }

  private String getNameValue(org.folg.gedcom.model.Name dqName) {
    String value = dqName.getValue();
    if (value == null) {
      return null;
    }

    int indexOfSlash;
//    For Japanese Missing Ordinance, there are only given names, so value is of the form: 'XXXX //'
//    If I delete the //, standard Names for Japanese assumes the first character is the last name. - simeki
    if (value.contains("//") && value.matches("[^a-zA-Z]+")) {
      return value.trim();
    }
    while ((indexOfSlash = value.indexOf('/')) >= 0){
      // If both characters around the slash are not a space, replace the slash with a space, otherwise just remove it.
      boolean replaceWithSpace = false;
      if(indexOfSlash > 0 && indexOfSlash < value.length() - 1) {
        char c = value.charAt(indexOfSlash - 1);
        if(c != ' ') {
          c = value.charAt(indexOfSlash + 1);
          if(c != ' ') {
            replaceWithSpace = true;
          }
        }
      }
      if(replaceWithSpace) {
        value = replaceCharAt(value, indexOfSlash, ' ');
      }
      else {
        value = deleteCharAt(value, indexOfSlash);
      }
    }
    return value.trim();
  }

  private List<NamePart> getNameParts(org.folg.gedcom.model.Name dqName) {
    List<NamePart> nameParts = new ArrayList<NamePart>(4);

    nameParts.addAll(newNamePartInstances(dqName.getPrefix(), NamePartType.Prefix));
    nameParts.addAll(newNamePartInstances(dqName.getGiven(), NamePartType.Given));
    nameParts.addAll(newNamePartInstances(getSurname(dqName), NamePartType.Surname));
    nameParts.addAll(newNamePartInstances(dqName.getSuffix(), NamePartType.Suffix));

    return nameParts.size() > 0 ? nameParts : null;
  }

  private String getSurname(org.folg.gedcom.model.Name dqName) {
    if ((dqName == null) || ((dqName.getValue() == null) && (dqName.getSurname() == null))) {
      return null;
    }

    String value = dqName.getSurname();
    if (value == null) {
      value = dqName.getValue();

      int slashIndex = value.indexOf('/');
      if (slashIndex >= 0) {
        StringBuilder builder = new StringBuilder(value);
        builder.replace(0, slashIndex + 1, "");
        value = builder.toString();
        slashIndex = value.indexOf('/');
        if (slashIndex >= 0) {
          builder.replace(slashIndex, builder.length(), "");
        }
        value = builder.toString().trim();
      } else {
        value = null;
      }
    }

    return value;
  }

  private List<NamePart> newNamePartInstances(String value, NamePartType type) {
    if(value == null) {
      return Collections.emptyList();
    }

    ArrayList<NamePart> nameParts = new ArrayList<NamePart>();

    String[] pieces = value.split(",\\s*");
    for (String piece : pieces){
      piece = piece.trim();
      if(!piece.equals("")) {
        NamePart namePart = new NamePart();
        namePart.setKnownType(type);
        namePart.setValue(piece);
        nameParts.add(namePart);
      }
    }

    return nameParts;
  }

  private String deleteCharAt(String value, int index) {
    if(value == null || index < 0 || index >= value.length()) {
      return value;
    }

    if(index == value.length() - 1) {
      return value.substring(0, index);
    }
    else if(index == 0) {
      return value.substring(index + 1);
    }
    else {
      return value.substring(0, index) + value.substring(index + 1);
    }
  }

  private String replaceCharAt(String value, int index, char c) {
    if(value == null || index < 0 || index >= value.length()) {
      return value;
    }

    if(index == value.length() - 1) {
      return value.substring(0, index) + c;
    }
    else if(index == 0) {
      return c + value.substring(index + 1);
    }
    else {
      return value.substring(0, index) + c + value.substring(index + 1);
    }
  }
}