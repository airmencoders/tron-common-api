/**
 * This is a seeder script to set up a mock Wing, with its groups, and squadrons, and members in a local Common API instance.
 *
 * To use locally, run "npm install" in this directory, and then "node seeder.js", or if you want to
 * run the requests thru a proxy if you have Spring Security enabled, you can use something like the "jwt-cli-utility"
 * and issue the command "PROXY=http://localhost:<proxy-port>/api/v1 node seeder.js".
 *
 * This script uses the json-patch endpoints for organization.
 *
 */
'use strict';
const fetch = require('node-fetch');

let url = process.env.PROXY || 'http://localhost:8088/api/v1';

async function addNewPerson(spec) {
    let [rank, firstName, middleName, lastName, email] = spec.split(/\s/);
    let urlString = `${url}/person`;

    if (rank === 'LtCol') rank = "Lt Col";
    else if (rank === '2Lt') rank = "2nd Lt";
    else if (rank === '1Lt') rank = "1st Lt";

    let resp = await fetch(urlString, {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({
            firstName, middleName, lastName, rank, email, branch: 'USAF'
        })
    });

    if (resp.status !== 201) throw new Error("Bad Add Person");
    
    let json = await resp.json();
    return json.id;
}

async function addNewOrg(type, name, parentId) {

    let urlString = `${url}/organization`;

    let resp = await fetch(urlString, {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({
            name, members: [], leader: null, parentOrganization: parentId, orgType: type.toUpperCase(), branchType: 'USAF'
        })
    });

    if (resp.status !== 201) throw new Error("Bad Add Org");
    
    let json = await resp.json();
    return json.id;
}

async function addMemberOrgs(id, orgs) {

    let patchOp = [];
    for (let org of orgs) {
        patchOp.push({ op: 'add', path: '/subordinateOrganizations/-', value: org })
    }
    
    let resp = await fetch(`${url}/organization/${id}`, {
        method: 'PATCH',
        headers: {
            'content-type': 'application/json-patch+json'
        },
        body: JSON.stringify(patchOp)
    });

    if (resp.status !== 200) throw new Error("Bad Add Member Org");
    
    let json = await resp.json();
    return json.id;
}

async function addLeader(id, leader) {

    let resp = await fetch(`${url}/organization/${id}`, {
        method: 'PATCH',
        headers: {
            'content-type': 'application/json-patch+json'
        },
        body: JSON.stringify([{ op: 'replace', path: '/leader', value: leader }])
    });

    if (resp.status !== 200) throw new Error("Bad Add Leader");

    let json = await resp.json();
    return json.id;
}

async function addMember(id, member) {

    let patchOp = [];
    patchOp.push({ op: 'add', path: '/members/-', value: member });

    let resp = await fetch(`${url}/organization/${id}`, {
        method: 'PATCH',
        headers: {
            'content-type': 'application/json-patch+json'
        },
        body: JSON.stringify(patchOp)
    });

    if (resp.status !== 200) throw new Error("Bad Add Member");
    
    let json = await resp.json();
    return json.id;
}

async function createOrgStructure(org, parent) {

    let newOrgId = await addNewOrg(org.type, org.name, parent);

    // add leader if present
    if (org.leader != null) {
        let leaderId = await addNewPerson(org.leader);
        await addLeader(newOrgId, leaderId);
    }

    // add members if present
    if (org.members != null) {
        for (let member of org.members) {
            let memberId = await addNewPerson(member);
            await addMember(newOrgId, memberId);
        }
    }

    // if has subordinate orgs
    if (org.units != null) {
        let subOrgUuids = [];

        // go create the suborgs themselves and store the uuids
        for (let unit of org.units) {
            subOrgUuids.push(await createOrgStructure(unit, newOrgId));
        }

        // go add the new suborgs to the parent
        await addMemberOrgs(newOrgId, subOrgUuids);
    }

    return newOrgId;
}

const orgStructure = {
    name: "181st IW",
    type : "WING",
    leader : 'Col JOHNNY A APPLESEED JA@AF.MIL',
    units : [
        {
            name : "181st ISRG",
            type : "GROUP",
            leader : 'Col FRED A SMITH FS@AF.MIL',
            units : [
                {
                    name : "137th IS",
                    type : "SQUADRON",
                    leader : 'LtCol JOEY A JOJO JJ@AF.MIL',
                    members : [
                        'SrA Marley Esperanza Windler Adah71@gmail.com',
                        'SSgt Turner Oren Pouros Danial44@hotmail.com',
                        'SrA Hester Davin Leuschke Dexter.Waelchi@yahoo.com',
                        'SrA Keaton Lauretta Schneider Estell_Stark42@gmail.com',
                        'MSgt Harrison Hershel Wintheiser Adrianna.Lakin79@hotmail.com',
                        '1Lt Chadd Kyra Wilderman Emanuel_Koss3@yahoo.com',
                        '2Lt Jacklyn Dereck Schulist Alexa.Keeling91@hotmail.com',
                        'TSgt Bradly Clarabelle Ruecker Erika.Cummings@hotmail.com',
                        'SSgt Nathanial Tod Oberbrunner Rylan29@yahoo.com',
                        'SrA Angelita Oma Gutmann Quinten.Wintheiser@gmail.com',
                        'AB Joe Stephanie Bode Rashad56@hotmail.com',
                        'A1C Nils Jaiden Swift Aurore82@gmail.com',
                        'TSgt Cydney Itzel Stamm Kennedy67@gmail.com',
                        'MSgt Ernesto Alec Dooley Andreane.Homenick@hotmail.com',
                        '1Lt Hattie Ulises Doyle Hester.Kemmer24@hotmail.com',
                        'SrA Jocelyn Raymundo Hermann Keely.Dach@hotmail.com',
                        '2Lt Velma Ettie Rippin George_Kulas@hotmail.com',
                        'AB Antonietta Shaun Kozey Kellie0@gmail.com',
                        'SSgt Trudie Casey OKon Dean22@hotmail.com',
                        'SSgt Cordia Ursula Lynch Herminia.Rice50@gmail.com',
                        '1Lt Tavares Conrad Becker Carolina.Kris69@yahoo.com',
                        '1Lt Rosamond Maya Raynor Anastacio_Heathcote@hotmail.com',
                        'SSgt Vaughn Jakob Okuneva Warren41@gmail.com',
                        '1Lt Hattie Claire Powlowski Ima.Okuneva@gmail.com',
                        'SMSgt Coty Kiley Rohan Juliana.Kerluke@gmail.com',
                        'AB Michale Whitney Stracke Melyna38@yahoo.com',
                        'MSgt Ezra Rubye Gibson Sim34@hotmail.com',
                        'AB Edwin Delphia Powlowski Kristoffer40@gmail.com',
                        '2Lt Marta Elfrieda Hoeger Ericka90@hotmail.com',
                        'TSgt Verona Santos Rohan Katelin28@hotmail.com',
                        '2Lt Isabel Dave Cole Ora53@gmail.com',
                        'A1C Davon Sadie Ryan Anastacio.Willms17@yahoo.com',
                        'SSgt Darrick Charlotte Gleason Fern96@hotmail.com',
                        'TSgt Lilly Lily Conroy Jensen_Kassulke@hotmail.com',
                        'TSgt Julia Jarvis Parisian Shaniya86@yahoo.com',
                        'AB Brooklyn Arnulfo Aufderhar Allene.Gulgowski10@gmail.com',
                        'A1C Velva Zachary Pollich Sylvan_Corwin5@yahoo.com',
                        'SMSgt Kole Presley Gorczany Cade_Huel24@gmail.com',
                        '1Lt Orrin Meagan Grimes Colten.Bradtke25@gmail.com',
                        'MSgt Tyler Liliane Heidenreich Alexzander_Harris@yahoo.com',
                        '1Lt Lonzo Greta Abernathy Ida.Paucek@gmail.com',
                        'MSgt Eva Elvera Bergstrom Destiney.Davis33@yahoo.com',
                        'A1C Ethel Mikayla Ortiz Marilyne_Sporer@hotmail.com',
                        'AB Clarabelle Lester Bauch Emerson82@hotmail.com',
                    ],
                    units : null,
                },
                {
                    name : "137th ISS",
                    type : "SQUADRON",
                    leader : 'LtCol CRAIG A SNELLER CS@AF.MIL',
                    members : [
                        '1Lt Dimitri Easton Zemlak Bud28@yahoo.com',
                        'SrA Stephanie Nicola Murray Amy.Wilkinson59@gmail.com',
                        '2Lt Arlene Skyla Weissnat Denis.Bernhard39@yahoo.com',
                        'TSgt Alta Casper Graham Domenic.Nikolaus@yahoo.com',
                        'AB Darryl Asha McLaughlin Colton_Treutel@gmail.com',
                        'SrA Russell Gabriella Kris Cielo26@yahoo.com',
                        'SrA Justus Tomasa Macejkovic Delaney.Schmitt@gmail.com',
                        'AB Felicity Lucy Hand Kelton.Larkin62@hotmail.com',
                        'TSgt Ashley Marcelle Fahey Jennyfer_Kirlin@hotmail.com',
                        'A1C Jayne Reese Funk Liliane.DAmore@hotmail.com',
                        'SMSgt Jaydon Nakia Orn Ken_Lueilwitz33@gmail.com',
                        'SrA Jovanny Jessy Durgan Rod.Reynolds86@hotmail.com',
                        '1Lt Cecelia Fabian Pouros Hal42@hotmail.com',
                        'SMSgt Iliana Edward Little Otilia14@hotmail.com',
                        '1Lt Ebba Bryon Bradtke Madisen_Auer@hotmail.com',
                        'AB Berneice Freeda Hartmann Abner_Dietrich97@gmail.com',
                        '1Lt Gudrun Chauncey Keeling Madisyn39@gmail.com',
                        'SrA Dawn Gardner Aufderhar Nathanael62@yahoo.com',
                        '2Lt Leanne Dakota Pouros Giuseppe_Ebert@hotmail.com',
                        'A1C Sheridan Prince Homenick Sandy36@yahoo.com',
                        'SrA Trevor Dangelo Gerlach Bulah_Schulist@yahoo.com',
                        'TSgt Era Destiney Miller Flavio49@gmail.com',
                        'AB Keanu Conner Johnston Danika_Bradtke@gmail.com',
                        'SMSgt Richard Beverly Will Mara_Crooks87@yahoo.com',
                        'AB Dedric Raven Marquardt Bernie_Simonis@yahoo.com',
                        'A1C Kian Jamal Kozey Courtney.Bradtke18@gmail.com',
                        'MSgt Imogene Morton Swaniawski Marcelina81@yahoo.com',
                        'TSgt Nicolette Audra Lubowitz Magali.Hartmann43@gmail.com',
                        'SSgt Sofia Vernice Wisoky Payton.Bruen@hotmail.com',
                        '2Lt Bertrand Shanie Weissnat Allan19@gmail.com',
                        'SrA Bonnie Noah Stamm Uriel84@yahoo.com',
                        'MSgt Dora Blair Bailey Sheldon_Herzog@gmail.com',
                        'MSgt Haley Griffin Spinka Vernie.Yundt23@hotmail.com',
                        'SSgt Lenora Edgardo Heidenreich Duncan49@gmail.com',
                        'SrA Vivianne Meghan Brekke Zaria7@hotmail.com',
                        'MSgt Kailey Pete Jenkins Vito17@hotmail.com',
                        'A1C Fae Alysa Larson Simone_Baumbach85@gmail.com',
                        '1Lt Devin Alexandria Crooks Maya96@yahoo.com',
                        'A1C Brandt Joel Huels Karli.Douglas@gmail.com',
                        'SSgt Russel Ethan Hegmann Herminio_Volkman86@gmail.com',
                        'A1C Dasia Ebba Murazik Mertie24@hotmail.com',
                        '2Lt Oren Joe Bode Kirk.McClure@yahoo.com',
                        'TSgt Earl Susie Champlin Berry.Rogahn@hotmail.com',
                        '2Lt Moriah Jerrell Brekke Xander.Bechtelar59@gmail.com',
                        '2Lt Colby Loraine Grant Nicolas32@hotmail.com',
                        'SrA Britney Landen Rippin Alex_Deckow@yahoo.com',
                        'SMSgt Joany Robyn Kirlin Raphael94@gmail.com',
                        'SrA Misael Vesta Bruen Robb_Larson18@yahoo.com',
                        'TSgt Ulices Maryse Kuphal Wilfrid.Fahey@hotmail.com',
                        'SrA Delaney Oren Armstrong Kaycee_Hermiston@yahoo.com',
                        'MSgt Eunice Cade Kerluke Genesis_Littel@yahoo.com',
                        '2Lt Pearlie Zachary Donnelly Selmer.Cartwright@yahoo.com',
                        'A1C Danyka Dejuan Rosenbaum Mohammed_Bailey97@yahoo.com',
                        'A1C Nash Brett Ledner Anjali15@yahoo.com',
                        'SMSgt Alexa Herta Moen Frankie.Jones@hotmail.com',
                        'A1C Berta Darius Kirlin Lenna8@yahoo.com',
                        '1Lt Zander Loy Koelpin Casey_Green45@hotmail.com',
                        'TSgt Novella Jeffery Hackett Kacie.Parker68@gmail.com',
                        'A1C Elmer Jakob Homenick Ahmad71@gmail.com',
                        'A1C Darlene Shawn McKenzie Lizeth.Kovacek78@yahoo.com',
                        '1Lt Roosevelt Raquel Roob Joan83@gmail.com',
                        'SrA Reanna Jamison Gleichner Marta13@yahoo.com',
                        'SSgt Chasity Edison Towne Alessandro.Goyette47@hotmail.com',
                        '1Lt Marge Jon Corkery Jannie17@gmail.com',
                    ],
                    units : null,
                },
                {
                    name : "137th OSS",
                    type : "SQUADRON",
                    leader : 'LtCol BECCA A LEADER BL@AF.MIL',
                    members : [
                        'MSgt Zora Karl Toy Demetris56@hotmail.com',
                        '1Lt Bernadine Lincoln Quigley Deborah.Prohaska@hotmail.com',
                        'MSgt Ivy Jaunita Zemlak Santa_Hammes@gmail.com',
                        'SrA Lucy Cathy Stanton Daija4@hotmail.com',
                        '2Lt Gladys Tito Mayert Sage60@hotmail.com',
                        'SSgt Janis Yvette Wiza Zola9@yahoo.com',
                        'SrA Alice Shemar Cole Angelo43@hotmail.com',
                        '1Lt Jacinthe Sonya Cummerata Reymundo72@yahoo.com',
                        'SrA Bulah Krystel Legros Francisco.Zemlak@yahoo.com',
                        'SSgt Kale Antwan Upton Sigrid.Effertz@gmail.com',
                        'SSgt Holden Leopold Brown Allen.Zieme52@gmail.com',
                        'SSgt Orion Karen Jaskolski Sienna96@yahoo.com',
                        'TSgt Dayna Rosina Conn Nettie.Funk50@hotmail.com',
                        '1Lt Deontae Constance Greenholt Harmon21@hotmail.com',
                        'SSgt Marilou Ozella Breitenberg Reagan_Gleason92@yahoo.com',
                        'AB Hailee Camylle Thiel Arne_Anderson@gmail.com',
                        'SrA Viola Alia Glover Monserrate_Oberbrunner@yahoo.com',
                        'AB Alexandrea Dustin Muller Ephraim.Hand@yahoo.com',
                        '2Lt Mathias Raymundo Runolfsdottir Ubaldo85@gmail.com',
                        'A1C Orlo Lyric Vandervort Joanie75@yahoo.com',
                        'A1C Marc Flavio Kemmer Kristofer70@hotmail.com',
                        '1Lt Tess Rashawn Blick Rex_Deckow7@hotmail.com',
                        'MSgt Isaiah Catherine Herman Herminia92@hotmail.com',
                        'SMSgt Jeffry Veda Toy Opal8@yahoo.com',
                        'SSgt Maryse Consuelo Dicki Isabel92@yahoo.com',
                        '2Lt Murl Bridgette Hand Arnaldo3@yahoo.com',
                        'AB Alexandra Ceasar Walter Quinten64@hotmail.com',
                        '2Lt Juvenal Xander Deckow Brook.Ortiz@yahoo.com',
                        'TSgt Pauline Lyda Abernathy Leilani_Wunsch15@gmail.com',
                        'AB Noemie Estelle Bednar Maude_Baumbach54@gmail.com',
                        'SrA Alycia Kellie Breitenberg Neva.Volkman@gmail.com',
                        'MSgt Marcellus Glennie West Kathleen_Hayes80@hotmail.com',
                        'TSgt Major Chris Casper Lindsay_Will@hotmail.com',
                        'AB Alexander Dylan Skiles Lewis18@hotmail.com',
                        'SrA Branson Maureen Renner Roel.Kihn53@hotmail.com',
                        'MSgt Fabian Manley Batz Cristal45@gmail.com',
                        'SSgt Kacey Colby Halvorson Myrtice46@hotmail.com',
                        '1Lt Julian Bernhard Senger Mac78@yahoo.com',
                        '2Lt Dejon Bennie Jacobs Eddie_Zulauf@hotmail.com',
                        'SMSgt Angela Lilla Kilback Presley_Frami@gmail.com',
                        'A1C Dedrick Mylene Wehner Molly.Huel@hotmail.com',
                        'AB Otho Zachariah Gorczany Joanny_Wisoky51@yahoo.com',
                        '2Lt Lorna Deonte Wiegand Rusty31@yahoo.com',
                        'SSgt Liliana Suzanne Green Nat.Walker@gmail.com',
                        'SSgt Danyka Ismael Ondricka Ava.Skiles@hotmail.com',
                        '2Lt Ashley Stacy Blick Maddison_Green@hotmail.com',
                        '1Lt Layne Angelina Oberbrunner Godfrey23@hotmail.com',
                        '1Lt Vilma Sherman Crist Karine30@gmail.com',
                        'SSgt Annie Alden Pollich Roberto_Kassulke36@yahoo.com',
                        'MSgt Shad Clark Kirlin Jamie_Wiza47@gmail.com',
                        'SMSgt Daron Nathanael Zboncak Emilie.Douglas34@hotmail.com',
                        'SMSgt Santos Flossie Beer Paige_Stanton@yahoo.com',
                        'AB Isabel Anastacio Veum Darron_Zemlak@yahoo.com',
                        'SMSgt Conner Brody Denesik Robbie.Bogan@yahoo.com',
                        '2Lt Lucas Myrtice Durgan Dwight41@gmail.com',
                        '2Lt Maxime Richmond Thompson Kasandra76@gmail.com',
                        'SrA Kelton Marta Brakus Brooks.Wintheiser@hotmail.com',
                        'TSgt Fannie Cordia Swift Frederick13@yahoo.com',
                    ],
                    units : null,
                },
            ],
        },
        {
            name : "181st MSG",
            type : "GROUP",
            leader : 'Col SARAH A GRAPESEED SG@AF.MIL',
            units : [
                {
                    name : "181st LRF",
                    type : "FLIGHT",
                    leader : 'Maj JIMBO A SUITER JSA@AF.MIL',
                    members : [
                        'MSgt Daryl Mercedes Goodwin Dariana.Kuphal67@hotmail.com',
                        'SMSgt Wilson Mark Welch Bradly76@gmail.com',
                        'SrA Clemmie Kelli Willms Cory13@gmail.com',
                        'MSgt Lawrence Jovany Predovic Leonel_Braun32@hotmail.com',
                        'SSgt Kailyn Rey Ziemann Vito.Kautzer@hotmail.com',
                        'A1C Craig Santiago Gorczany Braeden_Greenfelder@gmail.com',
                        'MSgt Freeda Aliza Turcotte Elton.Brakus68@yahoo.com',
                        'SrA Erling Harrison Johnston Olen.Hessel@gmail.com',
                        '1Lt Anabelle Lucile Mohr Otilia_Hoppe21@hotmail.com',
                        'A1C Eldred Weldon Hodkiewicz Pearlie65@gmail.com',
                        'SrA Desmond Marlin Lebsack Viva96@yahoo.com',
                        'TSgt Michale Maymie Carroll Ernest_Kris@hotmail.com',
                        '2Lt Brook Hyman Schaefer Vivien_Kiehn89@yahoo.com',
                        '2Lt Monica Ayden Hahn Myra3@hotmail.com',
                        '2Lt Lloyd Montana Champlin Juana88@gmail.com',
                        'MSgt Krista Darrel Kub Karen.Botsford25@gmail.com',
                        'SMSgt Joy Gussie Spencer Ava_Witting8@yahoo.com',
                        'A1C Paul Kayli DuBuque Conner66@hotmail.com',
                        'SrA Sunny Chandler Bailey Franz74@gmail.com',
                        'MSgt Karley Evalyn Wyman Erika_Klocko@hotmail.com',
                        'TSgt Edwina Raina Carter Serenity.King8@gmail.com',
                        '1Lt Talia Freeda Spencer Josephine94@yahoo.com',
                        '2Lt Godfrey Berta Miller Abdullah_Klocko@yahoo.com',
                        'SMSgt Melisa Bria Bosco Velva.Lubowitz@gmail.com',
                        'AB Antone Aileen Jaskolski Alfonzo.Lemke35@yahoo.com',
                        'SrA Werner Harley Jacobs Lola8@gmail.com',
                        'TSgt Dorothea Tanya Waelchi Claude.Schoen@gmail.com',
                        'TSgt Garrick Kathlyn Russel Demarcus64@yahoo.com',
                        'MSgt Amya Elenora White Aniya51@yahoo.com',
                        'SMSgt Erika Norberto Lynch Adrianna7@yahoo.com',
                        '1Lt Josiane Kraig Sawayn Obie34@gmail.com',
                        '2Lt Narciso Sarai Quitzon Max.Schaden@yahoo.com',
                        'A1C Schuyler Salvador Robel Tyreek49@gmail.com',
                        'SSgt Carli Lia Senger Graciela.Witting@hotmail.com',
                        'SMSgt Gerardo Eladio Rempel Mellie22@gmail.com',
                        'SSgt Kelly Davon Halvorson Nakia_Haag25@gmail.com',
                    ],
                    units : null,
                },
                {
                    name : "181st CF",
                    leader : 'Maj ARM A STRONG AS2@AF.MIL',
                    type : "FLIGHT",
                    members : [
                        'TSgt Jalon Dasia Crona Cielo_Mayert51@hotmail.com',
                        'MSgt Prudence Marcus Ward Lonny_Turner49@gmail.com',
                        'SSgt Jovanny Aryanna Parker Keith_Hintz@hotmail.com',
                        'A1C Fredrick Raphaelle Bartell Modesto.Wilkinson@gmail.com',
                        '2Lt Sibyl Velda Rice Ora_Muller44@gmail.com',
                        'SSgt Phyllis Christina Flatley Reece.Kihn37@hotmail.com',
                        '1Lt Lora Orland Jacobi Luther34@gmail.com',
                        '2Lt Gustave Sydnie Schmitt Cassandra_Schinner86@yahoo.com',
                        'SMSgt Claudia Yasmeen Rippin Adalberto_Lockman82@yahoo.com',
                        'SMSgt Adriel Jacinto Mann Therese_Kling69@hotmail.com',
                        'TSgt Griffin Jacinthe Donnelly Elroy.Lemke@hotmail.com',
                        'SSgt Giovanni Chauncey Tillman Zander28@yahoo.com',
                        '1Lt Madie Denis Monahan King.Wilkinson37@gmail.com',
                        'TSgt Christiana Mariela Beier Jamaal57@gmail.com',
                        'SMSgt Rhianna Bailey Frami Faye26@gmail.com',
                        'MSgt Alexandro Darien Veum Robyn89@yahoo.com',
                        'SrA Susan Dejon Barton Ottilie_Deckow@yahoo.com',
                        'MSgt Mikayla Denis Torp Libby_Halvorson31@hotmail.com',
                        'SSgt Wiley Vaughn Cormier Emmie_Wehner5@yahoo.com',
                        'MSgt Arely Rosalinda West Kiarra.Barton77@gmail.com',
                        '2Lt Shad Oswald Graham Ettie.Tromp@gmail.com',
                        'SMSgt Rodrick Gwen Conn Anita_Bauch@yahoo.com',
                        'AB Tad Orrin Davis Ilene.Dietrich64@yahoo.com',
                        'AB Meghan Lucy Weber Newell.Turcotte@hotmail.com',
                        'AB Isabell Ora Dietrich Domenica_Kuhn15@yahoo.com',
                        'TSgt Sadye Vicky Gutkowski Rashad6@hotmail.com',
                        'SMSgt Trystan Stefan Abshire Precious.Daugherty@hotmail.com',
                        '1Lt Johathan Claude Balistreri Kianna.Nienow83@hotmail.com',
                        '2Lt Ahmed Alexa Boyle Harmony57@gmail.com',
                        'AB Lauretta Jaiden Jast Lucio.Bergnaum34@hotmail.com',
                        'A1C Leda Cordell Graham Lucie_Nikolaus@gmail.com',
                        'A1C Billie Napoleon Fadel Chase.Rodriguez56@hotmail.com',
                        '1Lt Neal Noe Morar Fred.Maggio62@yahoo.com',
                        '2Lt Evan Gene Rice Estell_Marquardt55@yahoo.com',
                    ],
                    units : null,
                }
            ],
        }
    ],
};

createOrgStructure(orgStructure, null);
